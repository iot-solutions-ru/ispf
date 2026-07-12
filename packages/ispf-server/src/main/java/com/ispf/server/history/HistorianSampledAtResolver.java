package com.ispf.server.history;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lab/benchmark helper: Scylla/Cassandra {@code timestamp} is millisecond-granularity, so high-rate
 * ingress with the same wall-clock ms overwrites rows. When spread is enabled, assigns a monotonic
 * +1ms {@code sampled_at} per series key so throughput benchmarks reflect unique rows.
 */
final class HistorianSampledAtResolver {

    private final ConcurrentHashMap<String, Long> lastSpreadSampleMs = new ConcurrentHashMap<>();

    Instant resolve(boolean spreadEnabled, String seriesKey, Instant observedAt, Instant wallNow) {
        if (!spreadEnabled) {
            return wallNow;
        }
        long wallMs = wallNow.toEpochMilli();
        long baseMs = wallMs;
        if (observedAt != null) {
            baseMs = Math.max(baseMs, observedAt.toEpochMilli());
        }
        long finalBaseMs = baseMs;
        long assignedMs = lastSpreadSampleMs.compute(seriesKey, (key, previousMs) -> {
            if (previousMs == null) {
                return finalBaseMs;
            }
            if (finalBaseMs > previousMs) {
                return finalBaseMs;
            }
            return previousMs + 1;
        });
        return Instant.ofEpochMilli(assignedMs);
    }
}
