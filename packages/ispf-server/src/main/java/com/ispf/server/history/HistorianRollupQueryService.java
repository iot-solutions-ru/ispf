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
    private final HistorianRollupSubscriptionIndex subscriptionIndex;
    private final AnalyticsProperties analyticsProperties;

    public HistorianRollupQueryService(
            ClickHouseHistorianRollupStore rollupStore,
            HistorianRollupSubscriptionIndex subscriptionIndex,
            AnalyticsProperties analyticsProperties
    ) {
        this.rollupStore = rollupStore;
        this.subscriptionIndex = subscriptionIndex;
        this.analyticsProperties = analyticsProperties;
    }

    public boolean isRollupQueryEnabled() {
        return analyticsProperties.materializerEnabled() && rollupStore.isConfigured();
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
        if (!isRollupQueryEnabled()) {
            return Optional.empty();
        }
        if (!rangeLongEnoughForRollup(from, to)) {
            return Optional.empty();
        }
        if (!subscriptionIndex.isSubscribed(objectPath, variableName, fieldName, bucket)) {
            return Optional.empty();
        }
        List<VariableHistoryService.VariableHistoryBucket> buckets = rollupStore.queryBuckets(
                objectPath,
                variableName,
                fieldName,
                bucket,
                from,
                to,
                maxBuckets
        );
        if (buckets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RollupQueryResult(buckets, "rollup"));
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
