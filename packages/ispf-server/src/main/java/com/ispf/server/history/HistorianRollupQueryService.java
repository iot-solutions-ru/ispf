package com.ispf.server.history;

import com.ispf.server.config.AnalyticsProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Rollup-first historian aggregate reads with raw fallback (BL-205).
 */
@Service
public class HistorianRollupQueryService {

    private final ClickHouseHistorianRollupStore rollupStore;
    private final AnalyticsProperties analyticsProperties;

    public HistorianRollupQueryService(
            ClickHouseHistorianRollupStore rollupStore,
            AnalyticsProperties analyticsProperties
    ) {
        this.rollupStore = rollupStore;
        this.analyticsProperties = analyticsProperties;
    }

    public Optional<RollupQueryResult> tryRollupQuery(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    ) {
        if (!rollupStore.isConfigured() || !rangeLongEnoughForRollup(from, to)) {
            return Optional.empty();
        }
        List<VariableHistoryService.VariableHistoryBucket> buckets;
        try {
            buckets = rollupStore.queryBuckets(
                    objectPath,
                    variableName,
                    fieldName,
                    bucket,
                    from,
                    to,
                    maxBuckets
            );
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        if (!covers(buckets, from, to, bucket)) {
            return Optional.empty();
        }
        return Optional.of(new RollupQueryResult(buckets, "rollup"));
    }

    /** A partial rollup must not hide the raw samples outside its first and last bucket. */
    private static boolean covers(
            List<VariableHistoryService.VariableHistoryBucket> buckets,
            Instant from,
            Instant to,
            Duration bucket
    ) {
        if (buckets.isEmpty()) {
            return false;
        }
        Instant first = buckets.getFirst().ts();
        Instant last = buckets.getLast().ts();
        return first != null
                && last != null
                && !first.isAfter(from.plus(bucket))
                && !last.isBefore(to.minus(bucket));
    }

    /**
     * Sliding 7d chart windows are often a few seconds short of 7×24h, and
     * {@link Duration#toDays()} truncates — those queries must still hit granules.
     */
    boolean rangeLongEnoughForRollup(Instant from, Instant to) {
        Duration range = Duration.between(from, to);
        if (range.isNegative() || range.isZero()) {
            return false;
        }
        int minDays = Math.max(0, analyticsProperties.rollupMinQueryRangeDays());
        Duration minRange = Duration.ofDays(minDays).minus(Duration.ofHours(12));
        if (minRange.isNegative()) {
            minRange = Duration.ZERO;
        }
        return range.compareTo(minRange) >= 0;
    }

    public record RollupQueryResult(
            List<VariableHistoryService.VariableHistoryBucket> buckets,
            String dataSource
    ) {
    }
}
