package com.ispf.server.history;

import com.ispf.server.config.HistorianTierProfile;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.VariableHistoryProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Read-only warm-tier ClickHouse queries for historian tier routing (BL-159).
 */
@Service
@ConditionalOnProperty(name = "ispf.historian.tiers.warm.enabled", havingValue = "true")
@ConditionalOnExpression("'${ispf.variable-history.store:jdbc}' != 'clickhouse' && '${ispf.variable-history.store:jdbc}' != 'cassandra' && '${ispf.variable-history.store:jdbc}' != 'scylla'")
class ClickHouseWarmTierQueryStore implements VariableHistoryQueryStore {

    private final ClickHouseVariableHistoryReader reader;
    private final boolean configured;

    ClickHouseWarmTierQueryStore(HistorianTierProperties tierProperties) {
        HistorianTierProfile warm = tierProperties.warmTier();
        VariableHistoryProperties.ClickHouse clickhouse = warm.getClickhouse();
        this.configured = warm.isEnabled()
                && warm.isClickHouseStore()
                && clickhouse.getUrl() != null
                && !clickhouse.getUrl().isBlank();
        this.reader = configured ? new ClickHouseVariableHistoryReader(clickhouse) : null;
    }

    boolean isConfigured() {
        return configured;
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
        requireConfigured();
        return reader.query(objectPath, variableName, fieldName, from, to, limit);
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
        requireConfigured();
        return reader.aggregateBuckets(objectPath, variableName, fieldName, from, to, bucket, maxBuckets);
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return false;
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException(
                    "Warm-tier ClickHouse is not configured (enable warm tier and set clickhouse.url)"
            );
        }
    }
}
