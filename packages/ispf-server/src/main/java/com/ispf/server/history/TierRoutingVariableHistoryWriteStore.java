package com.ispf.server.history;

import com.ispf.server.config.HistorianTierProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes historian writes: hot (JDBC) for recent samples, warm (ClickHouse) for older observedAt (BL-159).
 */
@Service
@Primary
@ConditionalOnProperty(name = "ispf.historian.tiers.warm.enabled", havingValue = "true")
@ConditionalOnExpression("'${ispf.variable-history.store:jdbc}' != 'clickhouse' && '${ispf.variable-history.store:jdbc}' != 'cassandra' && '${ispf.variable-history.store:jdbc}' != 'scylla'")
class TierRoutingVariableHistoryWriteStore implements VariableHistoryWriteStore {

    private static final Logger log = LoggerFactory.getLogger(TierRoutingVariableHistoryWriteStore.class);

    private final HistorianTierProperties tierProperties;
    private final JdbcVariableHistoryWriteStore hotStore;
    private final ClickHouseWarmTierWriteStore warmStore;

    TierRoutingVariableHistoryWriteStore(
            HistorianTierProperties tierProperties,
            JdbcVariableHistoryWriteStore hotStore,
            ClickHouseWarmTierWriteStore warmStore
    ) {
        this.tierProperties = tierProperties;
        this.hotStore = hotStore;
        this.warmStore = warmStore;
    }

    @Override
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        Instant hotCutoff = hotCutoff();
        List<VariableHistoryWriteRecord> hotRecords = new ArrayList<>();
        List<VariableHistoryWriteRecord> warmRecords = new ArrayList<>();
        for (VariableHistoryWriteRecord record : records) {
            if (isWarmTierSample(record, hotCutoff)) {
                warmRecords.add(record);
            } else {
                hotRecords.add(record);
            }
        }
        if (!hotRecords.isEmpty()) {
            hotStore.appendBatch(hotRecords);
            if (tierProperties.hotTier().isDualWriteEnabled() && warmStore.isConfigured()) {
                warmStore.appendBestEffort(hotRecords);
            }
        }
        if (!warmRecords.isEmpty()) {
            if (warmStore.isConfigured()) {
                warmStore.appendBatch(warmRecords);
            } else {
                log.warn(
                        "Warm-tier ClickHouse not configured; falling back to hot JDBC for {} aged samples",
                        warmRecords.size()
                );
                hotStore.appendBatch(warmRecords);
            }
        }
    }

    @Override
    public void appendOne(VariableHistoryWriteRecord record) {
        appendBatch(List.of(record));
    }

    static boolean isWarmTierSample(VariableHistoryWriteRecord record, Instant hotCutoff) {
        Instant sampleTime = effectiveSampleTime(record);
        return sampleTime.isBefore(hotCutoff);
    }

    static Instant effectiveSampleTime(VariableHistoryWriteRecord record) {
        return record.observedAt() != null ? record.observedAt() : record.sampledAt();
    }

    private Instant hotCutoff() {
        int hotRetentionDays = Math.max(tierProperties.hotTier().getRetentionDays(), 1);
        return Instant.now().minus(hotRetentionDays, ChronoUnit.DAYS);
    }
}
