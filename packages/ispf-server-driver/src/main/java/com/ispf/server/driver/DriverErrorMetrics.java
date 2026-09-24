package com.ispf.server.driver;

import com.ispf.driver.DriverErrorKind;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts driver failures by {@link DriverErrorKind} and operation so dashboards can separate
 * "network flapping" (transient) from "operator misconfiguration" (configuration) and
 * "driver bug" (unclassified / permanent). Exposed as {@code ispf.driver.errors.total}
 * with tags {@code driver}, {@code op}, {@code kind}; in-memory totals are available for
 * diagnostics even without a registry.
 */
@Service
public class DriverErrorMetrics {

    public enum Operation {
        CONNECT("connect"),
        POLL("poll"),
        WRITE("write");

        private final String tag;

        Operation(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    static final String METRIC_NAME = "ispf.driver.errors.total";

    private final Optional<MeterRegistry> meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final EnumMap<DriverErrorKind, AtomicLong> totalsByKind = new EnumMap<>(DriverErrorKind.class);

    public DriverErrorMetrics(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (DriverErrorKind kind : DriverErrorKind.values()) {
            totalsByKind.put(kind, new AtomicLong());
        }
    }

    /** Record one failure; returns the resolved kind so callers can log it. */
    public DriverErrorKind record(String driverId, Operation operation, Throwable error) {
        DriverErrorKind kind = DriverErrorKind.classify(error);
        totalsByKind.get(kind).incrementAndGet();
        meterRegistry.ifPresent(registry -> {
            String driver = driverId == null || driverId.isBlank() ? "unknown" : driverId;
            String key = driver + '|' + operation.tag() + '|' + kind.tag();
            counters.computeIfAbsent(key, k -> Counter.builder(METRIC_NAME)
                    .description("Driver operation failures by classification")
                    .tag("driver", driver)
                    .tag("op", operation.tag())
                    .tag("kind", kind.tag())
                    .register(registry)).increment();
        });
        return kind;
    }

    /** Process-wide totals since start, independent of Micrometer. */
    public Map<DriverErrorKind, Long> totals() {
        EnumMap<DriverErrorKind, Long> snapshot = new EnumMap<>(DriverErrorKind.class);
        totalsByKind.forEach((kind, value) -> snapshot.put(kind, value.get()));
        return snapshot;
    }
}
