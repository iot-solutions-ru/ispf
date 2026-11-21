package com.ispf.server.history;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.relational.JdbcUtcTimestamps;
import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Chart-prep: JDBC writer into {@code variable_sample_observed_rollups}.
 * Walks a {@code sampled_at} arrival cursor and recomputes each touched {@code observed_at}
 * bucket from raw rows still present (late points rewrite the bucket). Default off.
 * Aggregate cutover is a separate opt-in ({@code ISPF_MSSQL_OBSERVED_ROLLUP_READ}). Not a SWITCH seal.
 */
public class JdbcMssqlObservedAtRollupMaterializer {

    private static final Logger log = LoggerFactory.getLogger(JdbcMssqlObservedAtRollupMaterializer.class);

    static final int[] BUCKET_SECONDS = {3_600, 86_400};

    private final JdbcTemplate jdbcTemplate;
    private final MssqlRelationalProperties mssqlProperties;
    private final VariableHistoryProperties historyProperties;
    private final ClusterProperties clusterProperties;
    private final RelationalDialect relationalDialect;
    private final TransactionTemplate transactionTemplate;

    private volatile TickResult lastTick = TickResult.skipped("not run yet");

    public JdbcMssqlObservedAtRollupMaterializer(
            JdbcTemplate jdbcTemplate,
            MssqlRelationalProperties mssqlProperties,
            VariableHistoryProperties historyProperties,
            ClusterProperties clusterProperties,
            RelationalDialect relationalDialect,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mssqlProperties = mssqlProperties;
        this.historyProperties = historyProperties;
        this.clusterProperties = clusterProperties;
        this.relationalDialect = relationalDialect;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean isEnabled() {
        return relationalDialect.kind() == RelationalDbKind.MSSQL
                && mssqlProperties.isSingleEngine()
                && mssqlProperties.isObservedRollupMaterializerEnabled()
                && historyProperties.isEnabled()
                && "jdbc".equalsIgnoreCase(trim(historyProperties.getStore()));
    }

    public TickResult lastTick() {
        return lastTick;
    }

    public TickResult materializeTick() {
        if (!isEnabled()) {
            lastTick = TickResult.skipped("observed_at rollup materializer disabled or not single-engine JDBC MSSQL");
            return lastTick;
        }
        if (!clusterProperties.isSchedulerActive()) {
            lastTick = TickResult.skipped("scheduler inactive");
            return lastTick;
        }
        long started = System.nanoTime();
        Instant now = Instant.now();
        int chunks = 0;
        int bucketsTouched = 0;
        try {
            for (int bucketSeconds : BUCKET_SECONDS) {
                TickProgress progress = materializeBucketWidth(bucketSeconds, now);
                chunks += progress.chunks();
                bucketsTouched += progress.bucketsTouched();
            }
            lastTick = TickResult.ok(chunks, bucketsTouched, Duration.ofNanos(System.nanoTime() - started).toMillis());
        } catch (RuntimeException ex) {
            log.warn("MSSQL observed_at rollup materializer tick failed: {}", ex.toString());
            lastTick = TickResult.failed(ex.getMessage());
        }
        return lastTick;
    }

    private TickProgress materializeBucketWidth(int bucketSeconds, Instant now) {
        Instant openSampledBucket = JdbcMssqlHistorianRollupMaterializer.floorToBucket(now, bucketSeconds);
        Instant purgeFloor = JdbcMssqlHistorianRollupMaterializer.floorToBucket(
                now.minus(mssqlProperties.getMaxRetentionDays(), ChronoUnit.DAYS), bucketSeconds
        );

        Optional<Instant> existing = loadSampledCoveredThrough(bucketSeconds);
        Instant covered;
        if (existing.isPresent()) {
            covered = JdbcMssqlHistorianRollupMaterializer.floorToBucket(existing.get(), bucketSeconds);
        } else {
            covered = seedSampledCoveredThrough(bucketSeconds, purgeFloor, openSampledBucket);
            saveSampledCoveredThrough(bucketSeconds, covered);
        }
        if (covered.isBefore(purgeFloor)) {
            covered = purgeFloor;
            saveSampledCoveredThrough(bucketSeconds, covered);
        }

        Duration chunk = alignedChunkDuration(bucketSeconds);
        int chunks = 0;
        int bucketsTouched = 0;
        Instant cursor = covered;
        int maxChunks = 8;
        while (cursor.isBefore(openSampledBucket) && chunks < maxChunks) {
            Instant from = cursor.isAfter(purgeFloor.plusSeconds(bucketSeconds))
                    ? cursor.minusSeconds(bucketSeconds)
                    : purgeFloor;
            Instant chunkEnd = cursor.plus(chunk);
            if (chunkEnd.isAfter(openSampledBucket)) {
                chunkEnd = openSampledBucket;
            }
            if (!chunkEnd.isAfter(cursor)) {
                break;
            }
            Instant previous = cursor;
            Instant advanceTo = chunkEnd;
            Integer touched = transactionTemplate.execute(status -> {
                int n = recomputeObservedBucketsTouchedBySampledRange(bucketSeconds, from, advanceTo, now);
                if (advanceTo.isAfter(previous)) {
                    saveSampledCoveredThrough(bucketSeconds, advanceTo);
                }
                return n;
            });
            bucketsTouched += touched == null ? 0 : touched;
            cursor = chunkEnd;
            chunks++;
        }
        return new TickProgress(chunks, bucketsTouched);
    }

    private Instant seedSampledCoveredThrough(int bucketSeconds, Instant purgeFloor, Instant openBucketStart) {
        Instant minSampled = jdbcTemplate.query(
                connection -> {
                    var statement = connection.prepareStatement(
                            """
                                    SELECT MIN(sampled_at) AS min_sampled
                                    FROM dbo.variable_samples
                                    WHERE value_double IS NOT NULL
                                      AND sampled_at >= ?
                                    """
                    );
                    JdbcUtcTimestamps.bind(statement, 1, purgeFloor);
                    return statement;
                },
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return JdbcUtcTimestamps.read(rs, "min_sampled");
                }
        );
        if (minSampled == null) {
            return openBucketStart;
        }
        Instant seed = JdbcMssqlHistorianRollupMaterializer.floorToBucket(minSampled, bucketSeconds);
        if (seed.isBefore(purgeFloor)) {
            seed = purgeFloor;
        }
        if (seed.isAfter(openBucketStart)) {
            seed = openBucketStart;
        }
        return seed;
    }

    /**
     * Rows whose {@code sampled_at} falls in {@code [from, to)} touch some {@code observed_at} buckets;
     * recompute those buckets from all raw rows still present in each bucket.
     */
    public int recomputeObservedBucketsTouchedBySampledRange(
            int bucketSeconds,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        Instant alignedFrom = JdbcMssqlHistorianRollupMaterializer.floorToBucket(fromInclusive, bucketSeconds);
        Instant alignedTo = JdbcMssqlHistorianRollupMaterializer.ceilToBucket(toExclusive, bucketSeconds);
        if (!alignedTo.isAfter(alignedFrom)) {
            return 0;
        }
        int defaultRetention = clampRetention(historyProperties.getRetentionDays());
        int cap = mssqlProperties.getMaxRetentionDays();
        Instant defaultCutoff = JdbcMssqlHistorianRollupMaterializer.floorToBucket(
                now.minus(defaultRetention, ChronoUnit.DAYS), bucketSeconds
        );
        Instant capCutoff = JdbcMssqlHistorianRollupMaterializer.floorToBucket(
                now.minus(cap, ChronoUnit.DAYS), bucketSeconds
        );
        return jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                            MERGE dbo.variable_sample_observed_rollups WITH (HOLDLOCK) AS target
                            USING (
                                SELECT
                                    vs.object_path,
                                    vs.variable_name,
                                    vs.field_name,
                                    touched.bucket_start,
                                    ? AS bucket_seconds,
                                    AVG(vs.value_double) AS avg_val,
                                    MIN(vs.value_double) AS min_val,
                                    MAX(vs.value_double) AS max_val,
                                    COUNT_BIG(*) AS sample_count
                                FROM (
                                    SELECT DISTINCT
                                        t.object_path,
                                        t.variable_name,
                                        t.field_name,
                                        DATE_BUCKET(SECOND, ?, t.observed_at) AS bucket_start
                                    FROM dbo.variable_samples t
                                    INNER JOIN dbo.object_variables ov2
                                        ON ov2.object_path = t.object_path
                                       AND ov2.name = t.variable_name
                                    WHERE ov2.history_enabled = 1
                                      AND t.field_name <> '$record'
                                      AND t.value_double IS NOT NULL
                                      AND t.sampled_at >= ?
                                      AND t.sampled_at < ?
                                ) touched
                                INNER JOIN dbo.variable_samples vs
                                    ON vs.object_path = touched.object_path
                                   AND vs.variable_name = touched.variable_name
                                   AND vs.field_name = touched.field_name
                                   AND vs.observed_at >= touched.bucket_start
                                   AND vs.observed_at < DATEADD(SECOND, ?, touched.bucket_start)
                                INNER JOIN dbo.object_variables ov
                                    ON ov.object_path = vs.object_path
                                   AND ov.name = vs.variable_name
                                WHERE ov.history_enabled = 1
                                  AND vs.field_name <> '$record'
                                  AND vs.value_double IS NOT NULL
                                  AND vs.observed_at >= CASE
                                        WHEN ov.history_retention_days IS NULL THEN ?
                                        WHEN ov.history_retention_days <= 0
                                          OR ov.history_retention_days > ? THEN ?
                                        ELSE DATE_BUCKET(
                                                SECOND,
                                                ?,
                                                DATEADD(DAY, -ov.history_retention_days, ?)
                                             )
                                      END
                                  AND vs.observed_at >= ?
                                  AND touched.bucket_start >= ?
                                GROUP BY vs.object_path, vs.variable_name, vs.field_name, touched.bucket_start
                            ) AS source
                            ON target.object_path = source.object_path
                               AND target.variable_name = source.variable_name
                               AND target.field_name = source.field_name
                               AND target.bucket_start = source.bucket_start
                               AND target.bucket_seconds = source.bucket_seconds
                            WHEN MATCHED THEN UPDATE SET
                                avg_val = source.avg_val,
                                min_val = source.min_val,
                                max_val = source.max_val,
                                sample_count = source.sample_count
                            WHEN NOT MATCHED THEN INSERT (
                                object_path, variable_name, field_name, bucket_start, bucket_seconds,
                                avg_val, min_val, max_val, sample_count
                            ) VALUES (
                                source.object_path, source.variable_name, source.field_name,
                                source.bucket_start, source.bucket_seconds,
                                source.avg_val, source.min_val, source.max_val, source.sample_count
                            );
                            """
            );
            int i = 1;
            statement.setInt(i++, bucketSeconds);
            statement.setInt(i++, bucketSeconds);
            JdbcUtcTimestamps.bind(statement, i++, alignedFrom);
            JdbcUtcTimestamps.bind(statement, i++, alignedTo);
            statement.setInt(i++, bucketSeconds);
            JdbcUtcTimestamps.bind(statement, i++, defaultCutoff);
            statement.setInt(i++, cap);
            JdbcUtcTimestamps.bind(statement, i++, capCutoff);
            statement.setInt(i++, bucketSeconds);
            JdbcUtcTimestamps.bind(statement, i++, now);
            JdbcUtcTimestamps.bind(statement, i++, capCutoff);
            JdbcUtcTimestamps.bind(statement, i++, capCutoff);
            return statement;
        });
    }

    public Optional<Instant> loadSampledCoveredThrough(int bucketSeconds) {
        return Optional.ofNullable(jdbcTemplate.query(
                connection -> {
                    var statement = connection.prepareStatement(
                            """
                                    SELECT sampled_covered_through
                                    FROM dbo.mssql_historian_observed_rollup_cursor
                                    WHERE bucket_seconds = ?
                                    """
                    );
                    statement.setInt(1, bucketSeconds);
                    return statement;
                },
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return JdbcUtcTimestamps.read(rs, "sampled_covered_through");
                }
        ));
    }

    private void saveSampledCoveredThrough(int bucketSeconds, Instant coveredThrough) {
        Instant aligned = JdbcMssqlHistorianRollupMaterializer.floorToBucket(coveredThrough, bucketSeconds);
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                            MERGE dbo.mssql_historian_observed_rollup_cursor WITH (HOLDLOCK) AS target
                            USING (SELECT ? AS bucket_seconds) AS source
                            ON target.bucket_seconds = source.bucket_seconds
                            WHEN MATCHED AND target.sampled_covered_through < ? THEN UPDATE SET
                                sampled_covered_through = ?,
                                updated_at = SYSUTCDATETIME()
                            WHEN NOT MATCHED THEN INSERT (bucket_seconds, sampled_covered_through, updated_at)
                            VALUES (?, ?, SYSUTCDATETIME());
                            """
            );
            statement.setInt(1, bucketSeconds);
            JdbcUtcTimestamps.bind(statement, 2, aligned);
            JdbcUtcTimestamps.bind(statement, 3, aligned);
            statement.setInt(4, bucketSeconds);
            JdbcUtcTimestamps.bind(statement, 5, aligned);
            return statement;
        });
    }

    private Duration alignedChunkDuration(int bucketSeconds) {
        int hours = mssqlProperties.getObservedRollupMaterializerChunkHours();
        long seconds = Math.max(bucketSeconds, hours * 3_600L);
        long aligned = ((seconds + bucketSeconds - 1L) / bucketSeconds) * bucketSeconds;
        return Duration.ofSeconds(aligned);
    }

    private int clampRetention(Integer retentionDays) {
        int cap = mssqlProperties.getMaxRetentionDays();
        if (retentionDays == null || retentionDays <= 0 || retentionDays > cap) {
            return cap;
        }
        return retentionDays;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record TickProgress(int chunks, int bucketsTouched) {
    }

    public record TickResult(String status, int chunks, int bucketsTouched, long elapsedMs, String detail) {
        public static TickResult ok(int chunks, int bucketsTouched, long elapsedMs) {
            return new TickResult("ok", chunks, bucketsTouched, elapsedMs, null);
        }

        public static TickResult skipped(String detail) {
            return new TickResult("skipped", 0, 0, 0, detail);
        }

        public static TickResult failed(String detail) {
            return new TickResult("failed", 0, 0, 0, detail == null ? "failed" : detail);
        }
    }
}
