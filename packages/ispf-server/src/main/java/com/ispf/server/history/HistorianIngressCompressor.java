package com.ispf.server.history;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exception deadband plus a swinging-door cone. Both limits at zero keep every sample.
 */
public final class HistorianIngressCompressor {

    private final double exceptionDeadband;
    private final double swingPerSecond;
    private final ConcurrentHashMap<String, Kept> lastKept = new ConcurrentHashMap<>();

    public HistorianIngressCompressor(double exceptionDeadband, double swingPerSecond) {
        this.exceptionDeadband = Math.max(0d, exceptionDeadband);
        this.swingPerSecond = Math.max(0d, swingPerSecond);
    }

    public boolean enabled() {
        return exceptionDeadband > 0d || swingPerSecond > 0d;
    }

    /**
     * @return true when the sample must be stored
     */
    public boolean accept(String seriesKey, Instant sampledAt, Double value) {
        if (!enabled() || value == null || sampledAt == null || seriesKey == null) {
            return true;
        }
        Kept previous = lastKept.get(seriesKey);
        if (previous == null) {
            lastKept.put(seriesKey, new Kept(sampledAt, value));
            return true;
        }
        double elapsedSec = Math.max(1e-3d, (sampledAt.toEpochMilli() - previous.sampledAt().toEpochMilli()) / 1000d);
        double limit = Math.max(exceptionDeadband, swingPerSecond * elapsedSec);
        if (Math.abs(value - previous.value()) <= limit) {
            return false;
        }
        lastKept.put(seriesKey, new Kept(sampledAt, value));
        return true;
    }

    private record Kept(Instant sampledAt, double value) {}
}
