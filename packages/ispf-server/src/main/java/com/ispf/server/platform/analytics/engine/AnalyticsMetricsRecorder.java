package com.ispf.server.platform.analytics.engine;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Prometheus metrics for analytics engine evaluations (BL-203).
 */
@Component
public class AnalyticsMetricsRecorder {

    private final Optional<MeterRegistry> meterRegistry;

    public AnalyticsMetricsRecorder(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEvaluation(int count, long latencyMs) {
        if (count <= 0) {
            return;
        }
        meterRegistry.ifPresent(registry -> {
            registry.counter("ispf.analytics.evaluations.total").increment(count);
            Timer.builder("ispf.analytics.evaluation.latency")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);
        });
    }
}
