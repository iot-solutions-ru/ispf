package com.ispf.server.history;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dual-write catch-up lag gauges (ADR-0035). Registered when dual-write is enabled.
 */
@Component
@ConditionalOnProperty(name = "ispf.variable-history.dual-write-enabled", havingValue = "true")
public class HistorianCatchUpMetrics {

    public HistorianCatchUpMetrics(HistorianRangeCatchUpService catchUp, ObjectProvider<MeterRegistry> meters) {
        MeterRegistry registry = meters.getIfAvailable();
        if (registry == null) {
            return;
        }
        Gauge.builder("ispf.historian.dual_write.catchup.ranges", catchUp, HistorianRangeCatchUpService::openRangeCount)
                .register(registry);
        Gauge.builder(
                        "ispf.historian.dual_write.catchup.oldest_age_ms",
                        catchUp,
                        service -> (double) service.oldestAgeMillis()
                )
                .register(registry);
        Gauge.builder("ispf.historian.dual_write.catchup.wal_bytes", catchUp, service -> (double) service.walBytes())
                .register(registry);
    }
}
