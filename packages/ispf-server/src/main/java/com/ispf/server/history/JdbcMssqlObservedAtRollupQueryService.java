package com.ispf.server.history;

import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Opt-in {@code /history/aggregate} path over {@code variable_sample_observed_rollups}.
 * Stitches raw head/tail around a closed rollup middle so live chart windows (to≈now) still work.
 * Default off. Not a SWITCH seal. Not Prod Yes B2. Lateness beyond one grain after the arrival
 * cursor may leave a closed middle bucket stale until the next materializer tick.
 */
public class JdbcMssqlObservedAtRollupQueryService {

    private static final Logger log = LoggerFactory.getLogger(JdbcMssqlObservedAtRollupQueryService.class);

    public static final String DATA_SOURCE = "observed_rollup";

    private final JdbcVariableSampleRollupStore rollupStore;
    private final JdbcMssqlObservedAtRollupMaterializer materializer;
    private final VariableHistoryQueryStore queryStore;
    private final MssqlRelationalProperties mssqlProperties;
    private final RelationalDialect relationalDialect;

    public JdbcMssqlObservedAtRollupQueryService(
            JdbcVariableSampleRollupStore rollupStore,
            JdbcMssqlObservedAtRollupMaterializer materializer,
            VariableHistoryQueryStore queryStore,
            MssqlRelationalProperties mssqlProperties,
            RelationalDialect relationalDialect
    ) {
        this.rollupStore = rollupStore;
        this.materializer = materializer;
        this.queryStore = queryStore;
        this.mssqlProperties = mssqlProperties;
        this.relationalDialect = relationalDialect;
    }

    public Optional<HistorianRollupQueryService.RollupQueryResult> tryAggregate(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    ) {
        if (!mssqlProperties.isObservedRollupReadEnabled()
                || !mssqlProperties.isSingleEngine()
                || relationalDialect.kind() != RelationalDbKind.MSSQL
                || maxBuckets <= 0) {
            return Optional.empty();
        }
        Optional<Integer> grain = materializedGrainSeconds(bucket);
        if (grain.isEmpty()) {
            return Optional.empty();
        }
        int bucketSeconds = grain.get();
        try {
            Optional<Instant> coveredOpt = materializer.loadSampledCoveredThrough(bucketSeconds);
            if (coveredOpt.isEmpty()) {
                return Optional.empty();
            }
            Instant coveredThrough = JdbcMssqlHistorianRollupMaterializer.floorToBucket(
                    coveredOpt.get(), bucketSeconds
            );
            // One-grain slack: open sampled_at bucket + late points up to one grain.
            Instant servableExclusive = coveredThrough.minusSeconds(bucketSeconds);
            Instant rollupFrom = JdbcMssqlHistorianRollupMaterializer.ceilToBucket(from, bucketSeconds);
            Instant rollupToExclusive = servableExclusive.isBefore(to) ? servableExclusive : floorExclusiveEnd(to, bucketSeconds);
            if (!rollupToExclusive.isAfter(rollupFrom)) {
                return Optional.empty();
            }

            List<VariableHistoryService.VariableHistoryBucket> middle = rollupStore.queryObservedBucketsBefore(
                    objectPath,
                    variableName,
                    fieldName,
                    rollupFrom,
                    rollupToExclusive,
                    bucketSeconds,
                    maxBuckets
            );

            List<VariableHistoryService.VariableHistoryBucket> tail = List.of();
            // Raw inclusive upper bound matches MssqlDialect (observed_at <= to).
            if (!rollupToExclusive.isAfter(to)) {
                tail = queryStore.aggregateBuckets(
                        objectPath, variableName, fieldName, rollupToExclusive, to, bucket, maxBuckets
                );
            }

            List<VariableHistoryService.VariableHistoryBucket> head = List.of();
            // Skip head when middle+tail already fill the newest-N window.
            if (from.isBefore(rollupFrom) && middle.size() + tail.size() < maxBuckets) {
                // DATETIME2(7) is 100 ns; minusNanos(1) half-up rounds to rollupFrom on the wire.
                Instant headTo = rollupFrom.minusNanos(100);
                if (!headTo.isBefore(from)) {
                    head = queryStore.aggregateBuckets(
                            objectPath, variableName, fieldName, from, headTo, bucket, maxBuckets
                    ).stream()
                            .filter(b -> b.ts() != null && b.ts().isBefore(rollupFrom))
                            .toList();
                }
            }

            List<VariableHistoryService.VariableHistoryBucket> merged = mergeNewest(
                    head, middle, tail, maxBuckets
            );
            return Optional.of(new HistorianRollupQueryService.RollupQueryResult(merged, DATA_SOURCE));
        } catch (RuntimeException ex) {
            log.debug("observed_at rollup aggregate failed; falling back to raw: {}", ex.toString());
            return Optional.empty();
        }
    }

    /**
     * Exclusive rollup end when {@code to} caps the window: only full buckets whose start is
     * {@code < floor(to)} when {@code to} is inside a bucket; when {@code to} is aligned, the
     * boundary sample at {@code to} is left to the raw tail ({@code observed_at <= to}).
     */
    static Instant floorExclusiveEnd(Instant to, int bucketSeconds) {
        return JdbcMssqlHistorianRollupMaterializer.floorToBucket(to, bucketSeconds);
    }

    static Optional<Integer> materializedGrainSeconds(Duration bucket) {
        if (bucket == null || bucket.getNano() != 0) {
            return Optional.empty();
        }
        long seconds = bucket.getSeconds();
        if (seconds == 3_600L) {
            return Optional.of(3_600);
        }
        if (seconds == 86_400L) {
            return Optional.of(86_400);
        }
        return Optional.empty();
    }

    static List<VariableHistoryService.VariableHistoryBucket> mergeNewest(
            List<VariableHistoryService.VariableHistoryBucket> head,
            List<VariableHistoryService.VariableHistoryBucket> middle,
            List<VariableHistoryService.VariableHistoryBucket> tail,
            int maxBuckets
    ) {
        Map<Instant, VariableHistoryService.VariableHistoryBucket> byTs = new LinkedHashMap<>();
        // Later put wins: head < middle < tail (disjoint by construction; collisions favor tail).
        putAll(byTs, head);
        putAll(byTs, middle);
        putAll(byTs, tail);
        List<VariableHistoryService.VariableHistoryBucket> all = new ArrayList<>(byTs.values());
        all.sort(Comparator.comparing(VariableHistoryService.VariableHistoryBucket::ts).reversed());
        if (all.size() > maxBuckets) {
            all = new ArrayList<>(all.subList(0, maxBuckets));
        }
        all.sort(Comparator.comparing(VariableHistoryService.VariableHistoryBucket::ts));
        return List.copyOf(all);
    }

    private static void putAll(
            Map<Instant, VariableHistoryService.VariableHistoryBucket> byTs,
            List<VariableHistoryService.VariableHistoryBucket> buckets
    ) {
        for (VariableHistoryService.VariableHistoryBucket bucket : buckets) {
            if (bucket != null && bucket.ts() != null) {
                byTs.put(bucket.ts(), bucket);
            }
        }
    }
}
