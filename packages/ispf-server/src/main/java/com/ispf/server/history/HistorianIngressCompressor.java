package com.ispf.server.history;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exception deadband plus a slope-change door. A straight ramp is not held forever:
 * a sample is stored at least once a minute. Both limits at zero keep every sample.
 */
public final class HistorianIngressCompressor {

    static final long MAX_HOLD_MILLIS = 60_000L;

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

    public void forgetPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        String child = prefix + "|";
        String descendant = prefix + ".";
        lastKept.keySet().removeIf(key -> key.equals(prefix) || key.startsWith(child) || key.startsWith(descendant));
    }

    /**
     * @return true when the sample must be stored
     */
    public boolean accept(String seriesKey, Instant sampledAt, Double value) {
        if (!enabled() || value == null || sampledAt == null || seriesKey == null) {
            return true;
        }
        if (!Double.isFinite(value)) {
            lastKept.remove(seriesKey);
            return true;
        }
        Kept previous = lastKept.get(seriesKey);
        if (previous == null) {
            lastKept.put(seriesKey, new Kept(sampledAt, value, null));
            return true;
        }
        long elapsedMs = sampledAt.toEpochMilli() - previous.sampledAt().toEpochMilli();
        if (elapsedMs <= 0 || elapsedMs >= MAX_HOLD_MILLIS) {
            lastKept.put(seriesKey, new Kept(sampledAt, value, null));
            return true;
        }
        double slope = (value - previous.value()) / (elapsedMs / 1000d);
        if (swingPerSecond > 0d) {
            boolean withinSlope = Math.abs(slope) <= swingPerSecond
                    && (previous.slope() == null || Math.abs(slope - previous.slope()) <= swingPerSecond);
            boolean withinBand = exceptionDeadband <= 0d || Math.abs(value - previous.value()) <= exceptionDeadband;
            if (withinSlope && withinBand) {
                lastKept.put(seriesKey, new Kept(previous.sampledAt(), previous.value(), slope));
                return false;
            }
        } else if (Math.abs(value - previous.value()) <= exceptionDeadband) {
            return false;
        }
        lastKept.put(seriesKey, new Kept(sampledAt, value, null));
        return true;
    }

    private record Kept(Instant sampledAt, double value, Double slope) {}
}
