package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Documented historian query SLO defaults (BL-161). Used for health probes and lab gates in follow-up work.
 */
@ConfigurationProperties(prefix = "ispf.variable-history.slo")
public class VariableHistorySloProperties {

    /** Max points in an aggregate query covered by the SLO. */
    private long aggregateMaxPoints = 1_000_000L;

    /** Target p95 latency for aggregate queries at {@link #aggregateMaxPoints} (milliseconds). */
    private long aggregateMaxLatencyMs = 2_000L;

    /** Max raw points in a trend query covered by the SLO. */
    private int rawQueryMaxPoints = 10_000;

    /** Target p95 latency for raw trend queries (milliseconds). */
    private long rawQueryMaxLatencyMs = 500L;

    /** Max points returned by CSV/JSON export in one request. */
    private int exportMaxPoints = 10_000;

    public long getAggregateMaxPoints() {
        return aggregateMaxPoints;
    }

    public void setAggregateMaxPoints(long aggregateMaxPoints) {
        this.aggregateMaxPoints = Math.max(1, aggregateMaxPoints);
    }

    public long getAggregateMaxLatencyMs() {
        return aggregateMaxLatencyMs;
    }

    public void setAggregateMaxLatencyMs(long aggregateMaxLatencyMs) {
        this.aggregateMaxLatencyMs = Math.max(1, aggregateMaxLatencyMs);
    }

    public int getRawQueryMaxPoints() {
        return rawQueryMaxPoints;
    }

    public void setRawQueryMaxPoints(int rawQueryMaxPoints) {
        this.rawQueryMaxPoints = Math.max(1, rawQueryMaxPoints);
    }

    public long getRawQueryMaxLatencyMs() {
        return rawQueryMaxLatencyMs;
    }

    public void setRawQueryMaxLatencyMs(long rawQueryMaxLatencyMs) {
        this.rawQueryMaxLatencyMs = Math.max(1, rawQueryMaxLatencyMs);
    }

    public int getExportMaxPoints() {
        return exportMaxPoints;
    }

    public void setExportMaxPoints(int exportMaxPoints) {
        this.exportMaxPoints = Math.max(1, exportMaxPoints);
    }
}
