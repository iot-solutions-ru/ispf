package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.runtime-telemetry")
public class RuntimeTelemetryProperties {

    private boolean enabled = true;

    private long coalesceMs = 1_000;

    /** Threads for scheduled per-lane telemetry coalesce flush (parallel topic lanes). */
    private int coalesceSchedulerThreads = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCoalesceMs() {
        return coalesceMs;
    }

    public void setCoalesceMs(long coalesceMs) {
        this.coalesceMs = coalesceMs;
    }

    public int getCoalesceSchedulerThreads() {
        return coalesceSchedulerThreads;
    }

    public void setCoalesceSchedulerThreads(int coalesceSchedulerThreads) {
        this.coalesceSchedulerThreads = Math.max(1, coalesceSchedulerThreads);
    }
}
