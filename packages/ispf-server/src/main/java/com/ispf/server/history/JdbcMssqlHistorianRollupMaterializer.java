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
import java.util.List;
import java.util.Optional;

/**
 * Gate C2: set-based JDBC rollup into {@code variable_sample_rollups} on {@code sampled_at}.
 * Separate from the ClickHouse analytics materializer. Default off. Not a SWITCH seal —
 * charts still scan raw rows by {@code observed_at}; app DELETE retention stays.
 */
public class JdbcMssqlHistorianRollupMaterializer {

    private static final Logger log = LoggerFactory.getLogger(JdbcMssqlHistorianRollupMaterializer.class);

    /** Hour and day only (align with DATE_BUCKET from 1900-01-01). */
    static final int[] BUCKET_SECONDS = {3_600, 86_400};

    private final JdbcTemplate jdbcTemplate;
    private final MssqlRelationalProperties mssqlProperties;
    private final VariableHistoryProperties historyProperties;
    private final ClusterProperties clusterProperties;
    private final RelationalDialect relationalDialect;
    private final TransactionTemplate transactionTemplate;

    private volatile TickResult lastTick = TickResult.skipped("not run yet");

    public JdbcMssqlHistorianRollupMaterializer(
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
                && mssqlProperties.isRollupMaterializerEnabled()
                && historyProperties.isEnabled()
                && "jdbc".equalsIgnoreCase(trim(historyProperties.getStore()));
    }

    public TickResult lastTick() {
        return lastTick;
    }

    public TickResult materializeTick() {
        if (!isEnabled()) {
            lastTick = TickResult.skipped("rollup materializer disabled or not single-engine JDBC MSSQL");
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
            log.warn("MSSQL JDBC rollup materializer tick failed: {}", ex.toString());
            lastTick = TickResult.failed(ex.getMessage());
        }
        return lastTick;
    }

    private TickProgress materializeBucketWidth(int bucketSeconds, Instant now) {
        Instant openBucketStart = floorToBucket(now, bucketSeconds);
        Instant purgeFloor = floorToBucket(now.minus(mssqlProperties.getMaxRetentionDays(), ChronoUnit.DAYS), bucketSeconds);

        Optional<Instant> existingCovered = loadCoveredThrough(bucketSeconds);
        Instant covered;
        if (existingCovered.isPresent()) {
            covered = floorToBucket(existingCovered.get(), bucketSeconds);
        } else {
            covered = seedCoveredThrough(bucketSeconds, purgeFloor, openBucketStart);
            saveCoveredThrough(bucketSeconds, covered);
        }
        if (covered.isBefore(purgeFloor)) {
            covered = purgeFloor;
            saveCoveredThrough(bucketSeconds, covered);
        }

        Duration chunk = alignedChunkDuration(bucketSeconds);
        int chunks = 0;
        int bucketsTouched = 0;
        Instant cursor = covered;

        while (cursor.isBefore(openBucketStart)) {
            Instant from = cursor.isAfter(purgeFloor.plusSeconds(bucketSeconds))
                    ? cursor.minusSeconds(bucketSeconds)
                    : purgeFloor;
            Instant chunkEnd = cursor.plus(chunk);
            if (chunkEnd.isAfter(openBucketStart)) {
                chunkEnd = openBucketStart;
            }
            if (!chunkEnd.isAfter(cursor)) {
                break;
            }
            Instant previous = cursor;
            bucketsTouched += mergeChunkAndAdvance(bucketSeconds, from, chunkEnd, previous, chunkEnd, now);
            cursor = chunkEnd;
            chunks++;
            if (chunks >= 8) {
                break;
            }
        }

        bucketsTouched += mergeSampledAtRange(bucketSeconds, openBucketStart, openBucketStart.plusSeconds(bucketSeconds), now);
        return new TickProgress(chunks, bucketsTouched);
    }

    private Duration alignedChunkDuration(int bucketSeconds) {
        long configuredSeconds = Duration.ofHours(mssqlProperties.getRollupMaterializerChunkHours()).getSeconds();
        long multiples = Math.max(1L, (configuredSeconds + bucketSeconds - 1L) / bucketSeconds);
        return Duration.ofSeconds(multiples * bucketSeconds);
    }

    private int mergeChunkAndAdvance(
            int bucketSeconds,
            Instant fromInclusive,
            Instant toExclusive,
            Instant previousCovered,
            Instant advanceTo,
            Instant now
    ) {
        Integer n = transactionTemplate.execute(status -> {
            int touched = mergeSampledAtRange(bucketSeconds, fromInclusive, toExclusive, now);
            if (advanceTo.isAfter(previousCovered)) {
                saveCoveredThrough(bucketSeconds, advanceTo);
            }
            return touched;
        });
        return n == null ? 0 : n;
    }

    private Instant seedCoveredThrough(int bucketSeconds, Instant purgeFloor, Instant openBucketStart) {
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
        Instant seed = floorToBucket(minSampled, bucketSeconds);
        if (seed.isBefore(purgeFloor)) {
            seed = purgeFloor;
        }
        if (seed.isAfter(openBucketStart)) {
            seed = openBucketStart;
        }
        return seed;
    }

    public int mergeSampledAtRange(int bucketSeconds, Instant fromInclusive, Instant toExclusive) {
        return mergeSampledAtRange(bucketSeconds, fromInclusive, toExclusive, Instant.now());
    }

    public int mergeSampledAtRange(int bucketSeconds, Instant fromInclusive, Instant toExclusive, Instant now) {
        Instant alignedFrom = floorToBucket(fromInclusive, bucketSeconds);
        Instant alignedTo = ceilToBucket(toExclusive, bucketSeconds);
        if (!alignedTo.isAfter(alignedFrom)) {
            return 0;
        }
        int defaultRetention = clampRetention(historyProperties.getRetentionDays());
        int cap = mssqlProperties.getMaxRetentionDays();
        Instant defaultCutoff = floorToBucket(now.minus(defaultRetention, ChronoUnit.DAYS), bucketSeconds);
        Instant capCutoff = floorToBucket(now.minus(cap, ChronoUnit.DAYS), bucketSeconds);
        return jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                            MERGE dbo.variable_sample_rollups WITH (HOLDLOCK) AS target
                            USING (
                                SELECT
                                    object_path,
                                    variable_name,
                                    field_name,
                                    bucket_start,
                                    ? AS bucket_seconds,
                                    AVG(value_double) AS avg_val,
                                    MIN(value_double) AS min_val,
                                    MAX(value_double) AS max_val,
                                    COUNT_BIG(*) AS sample_count
                                FROM (
                                    SELECT
                                        vs.object_path,
                                        vs.variable_name,
                                        vs.field_name,
                                        DATE_BUCKET(SECOND, ?, vs.sampled_at) AS bucket_start,
                                        vs.value_double
                                    FROM dbo.variable_samples vs
                                    INNER JOIN dbo.object_variables ov
                                        ON ov.object_path = vs.object_path
                                       AND ov.name = vs.variable_name
                                    WHERE ov.history_enabled = 1
                                      AND vs.field_name <> '$record'
                                      AND vs.value_double IS NOT NULL
                                      AND vs.sampled_at >= ?
                                      AND vs.sampled_at < ?
                                      AND vs.sampled_at >= CASE
                                            WHEN ov.history_retention_days IS NULL THEN ?
                                            WHEN ov.history_retention_days <= 0
                                              OR ov.history_retention_days > ? THEN ?
                                            ELSE DATE_BUCKET(
                                                    SECOND,
                                                    ?,
                                                    DATEADD(DAY, -ov.history_retention_days, ?)
                                                 )
                                          END
                                ) bucketed
                                WHERE bucket_start >= ?
                                GROUP BY object_path, variable_name, field_name, bucket_start
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
            statement.setInt(1, bucketSeconds);
            statement.setInt(2, bucketSeconds);
            JdbcUtcTimestamps.bind(statement, 3, alignedFrom);
            JdbcUtcTimestamps.bind(statement, 4, alignedTo);
            // NULL retention → defaultCutoff (clamped defaultRetention)
            JdbcUtcTimestamps.bind(statement, 5, defaultCutoff);
            // <=0 or >cap → capCutoff
            statement.setInt(6, cap);
            JdbcUtcTimestamps.bind(statement, 7, capCutoff);
            // ELSE: floor DATEADD(DAY, -history_retention_days, now) to bucket
            statement.setInt(8, bucketSeconds);
            JdbcUtcTimestamps.bind(statement, 9, now);
            JdbcUtcTimestamps.bind(statement, 10, alignedFrom);
            return statement;
        });
    }

    public Optional<Instant> loadCoveredThrough(int bucketSeconds) {
        List<Instant> rows = jdbcTemplate.query(
                connection -> {
                    var statement = connection.prepareStatement(
                            """
                                    SELECT covered_through
                                    FROM dbo.mssql_historian_rollup_cursor
                                    WHERE bucket_seconds = ?
                                    """
                    );
                    statement.setInt(1, bucketSeconds);
                    return statement;
                },
                (rs, rowNum) -> JdbcUtcTimestamps.read(rs, "covered_through")
        );
        return rows.stream().findFirst();
    }

    public void saveCoveredThrough(int bucketSeconds, Instant coveredThrough) {
        Instant aligned = floorToBucket(coveredThrough, bucketSeconds);
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                            MERGE dbo.mssql_historian_rollup_cursor WITH (HOLDLOCK) AS target
                            USING (SELECT ? AS bucket_seconds) AS source
                            ON target.bucket_seconds = source.bucket_seconds
                            WHEN MATCHED AND target.covered_through < ? THEN UPDATE SET
                                covered_through = ?,
                                updated_at = SYSUTCDATETIME()
                            WHEN NOT MATCHED THEN INSERT (bucket_seconds, covered_through, updated_at)
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

    public static Instant floorToBucket(Instant instant, int bucketSeconds) {
        long epoch = instant.getEpochSecond();
        long floored = epoch - Math.floorMod(epoch, bucketSeconds);
        return Instant.ofEpochSecond(floored);
    }

    public static Instant ceilToBucket(Instant instant, int bucketSeconds) {
        long epoch = instant.getEpochSecond();
        long rem = Math.floorMod(epoch, bucketSeconds);
        if (rem == 0 && instant.getNano() == 0) {
            return Instant.ofEpochSecond(epoch);
        }
        return Instant.ofEpochSecond(epoch - rem + bucketSeconds);
    }

    private int clampRetention(int days) {
        int cap = mssqlProperties.getMaxRetentionDays();
        if (days <= 0 || days > cap) {
            return cap;
        }
        return days;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record TickProgress(int chunks, int bucketsTouched) {
    }

    public record TickResult(boolean ran, String detail, int chunks, int bucketsTouched, long durationMs) {
        static TickResult skipped(String detail) {
            return new TickResult(false, detail, 0, 0, 0L);
        }

        static TickResult failed(String detail) {
            return new TickResult(false, detail == null ? "failed" : detail, 0, 0, 0L);
        }

        static TickResult ok(int chunks, int bucketsTouched, long durationMs) {
            return new TickResult(true, "ok", chunks, bucketsTouched, durationMs);
        }
    }
}
