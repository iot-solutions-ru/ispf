package com.ispf.server.history;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sole-store spool gauges: bytes, lag, oldest age, replayed batches, errors, quota.
 */
@Component
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "clickhouse")
public class HistorianSpoolMetrics {

    public HistorianSpoolMetrics(ClickHouseVariableHistoryStore store, ObjectProvider<MeterRegistry> meters) {
        MeterRegistry registry = meters.getIfAvailable();
        if (registry == null) {
            return;
        }
        Gauge.builder("ispf.historian.spool.bytes", store, HistorianSpoolMetrics::bytes).register(registry);
        Gauge.builder("ispf.historian.spool.lag", store, HistorianSpoolMetrics::lag).register(registry);
        Gauge.builder("ispf.historian.spool.oldest_age_ms", store, HistorianSpoolMetrics::age).register(registry);
        Gauge.builder("ispf.historian.spool.replayed", store, HistorianSpoolMetrics::replayed).register(registry);
        Gauge.builder("ispf.historian.spool.errors", store, HistorianSpoolMetrics::errors).register(registry);
        Gauge.builder("ispf.historian.spool.quota", store, HistorianSpoolMetrics::quota).register(registry);
    }

    private static double bytes(ClickHouseVariableHistoryStore store) {
        HistorianDiskSpool.Stats stats = store.spoolStats();
        return stats == null ? 0 : stats.bytes();
    }

    private static double lag(ClickHouseVariableHistoryStore store) {
        HistorianDiskSpool.Stats stats = store.spoolStats();
        return stats == null ? 0 : stats.unackedRecords();
    }

    private static double age(ClickHouseVariableHistoryStore store) {
        HistorianDiskSpool.Stats stats = store.spoolStats();
        return stats == null ? 0 : stats.oldestAgeMillis();
    }

    private static double replayed(ClickHouseVariableHistoryStore store) {
        HistorianDiskSpool.Stats stats = store.spoolStats();
        return stats == null ? 0 : stats.replayedBatches();
    }

    private static double errors(ClickHouseVariableHistoryStore store) {
        HistorianDiskSpool.Stats stats = store.spoolStats();
        return stats == null ? 0 : stats.errors();
    }

    private static double quota(ClickHouseVariableHistoryStore store) {
        HistorianDiskSpool.Stats stats = store.spoolStats();
        return stats != null && stats.quotaHit() ? 1 : 0;
    }
}
