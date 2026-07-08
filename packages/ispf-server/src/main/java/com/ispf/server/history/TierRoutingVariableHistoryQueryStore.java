package com.ispf.server.history;

import com.ispf.server.config.HistorianTierProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Routes historian reads: hot (JDBC) for recent samples, warm (ClickHouse) for older ranges (BL-159).
 */
@Service
@Primary
@ConditionalOnProperty(name = "ispf.historian.tiers.warm.enabled", havingValue = "true")
@ConditionalOnExpression("'${ispf.variable-history.store:jdbc}' != 'clickhouse' && '${ispf.variable-history.store:jdbc}' != 'cassandra' && '${ispf.variable-history.store:jdbc}' != 'scylla'")
class TierRoutingVariableHistoryQueryStore implements VariableHistoryQueryStore {

    private final HistorianTierProperties tierProperties;
    private final JdbcVariableHistoryQueryStore hotStore;
    private final ClickHouseWarmTierQueryStore warmStore;

    TierRoutingVariableHistoryQueryStore(
            HistorianTierProperties tierProperties,
            JdbcVariableHistoryQueryStore hotStore,
            ClickHouseWarmTierQueryStore warmStore
    ) {
        this.tierProperties = tierProperties;
        this.hotStore = hotStore;
        this.warmStore = warmStore;
    }

    @Override
    public List<VariableHistoryService.VariableHistorySample> query(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) {
        if (!warmRoutingActive()) {
            return hotStore.query(objectPath, variableName, fieldName, from, to, limit);
        }

        Instant hotCutoff = hotCutoff();
        Instant resolvedTo = to != null ? to : Instant.now();

        if (from != null && to != null) {
            if (!from.isBefore(hotCutoff)) {
                return hotStore.query(objectPath, variableName, fieldName, from, to, limit);
            }
            if (resolvedTo.isBefore(hotCutoff)) {
                return warmStore.query(objectPath, variableName, fieldName, from, to, limit);
            }
            List<VariableHistoryService.VariableHistorySample> merged = new ArrayList<>();
            merged.addAll(warmStore.query(objectPath, variableName, fieldName, from, hotCutoff, limit));
            int remaining = Math.max(0, limit - merged.size());
            if (remaining > 0) {
                merged.addAll(hotStore.query(objectPath, variableName, fieldName, hotCutoff, resolvedTo, remaining));
            }
            return capSamples(merged, limit);
        }

        List<VariableHistoryService.VariableHistorySample> hotSamples =
                hotStore.query(objectPath, variableName, fieldName, hotCutoff, resolvedTo, limit);
        if (hotSamples.size() >= limit) {
            return hotSamples;
        }
        int remaining = limit - hotSamples.size();
        List<VariableHistoryService.VariableHistorySample> warmSamples = warmStore.query(
                objectPath,
                variableName,
                fieldName,
                null,
                hotCutoff.minus(1, ChronoUnit.MILLIS),
                remaining
        );
        List<VariableHistoryService.VariableHistorySample> merged = new ArrayList<>(warmSamples.size() + hotSamples.size());
        merged.addAll(warmSamples);
        merged.addAll(hotSamples);
        return capSamples(merged, limit);
    }

    @Override
    public List<VariableHistoryService.VariableHistoryBucket> aggregateBuckets(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    ) {
        if (!warmRoutingActive()) {
            return hotStore.aggregateBuckets(objectPath, variableName, fieldName, from, to, bucket, maxBuckets);
        }

        Instant hotCutoff = hotCutoff();
        if (!from.isBefore(hotCutoff)) {
            return hotStore.aggregateBuckets(objectPath, variableName, fieldName, from, to, bucket, maxBuckets);
        }
        if (to.isBefore(hotCutoff)) {
            return warmStore.aggregateBuckets(objectPath, variableName, fieldName, from, to, bucket, maxBuckets);
        }

        List<VariableHistoryService.VariableHistoryBucket> merged = new ArrayList<>();
        merged.addAll(warmStore.aggregateBuckets(objectPath, variableName, fieldName, from, hotCutoff, bucket, maxBuckets));
        merged.addAll(hotStore.aggregateBuckets(objectPath, variableName, fieldName, hotCutoff, to, bucket, maxBuckets));
        merged.sort(Comparator.comparing(VariableHistoryService.VariableHistoryBucket::ts));
        if (merged.size() > maxBuckets) {
            return merged.subList(merged.size() - maxBuckets, merged.size());
        }
        return merged;
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return hotStore.supportsApplicationRetentionPurge();
    }

    private boolean warmRoutingActive() {
        return tierProperties.warmTier().isEnabled() && warmStore.isConfigured();
    }

    private Instant hotCutoff() {
        int hotRetentionDays = Math.max(tierProperties.hotTier().getRetentionDays(), 1);
        return Instant.now().minus(hotRetentionDays, ChronoUnit.DAYS);
    }

    private static List<VariableHistoryService.VariableHistorySample> capSamples(
            List<VariableHistoryService.VariableHistorySample> samples,
            int limit
    ) {
        if (samples.size() <= limit) {
            return samples;
        }
        return samples.subList(samples.size() - limit, samples.size());
    }
}
