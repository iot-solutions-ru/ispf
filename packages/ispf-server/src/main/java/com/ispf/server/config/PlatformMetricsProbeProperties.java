package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.platform-metrics-probe")
public class PlatformMetricsProbeProperties {

    /**
     * When true, probe sync runs continuously (feeds self-diagnostic dashboards).
     * Load-diagnostics UI can still force-enable via runtime flag.
     */
    private boolean enabled = true;

    /** Seed probe device + platform-metrics dashboard on startup. */
    private boolean ensureOnStartup = true;

    private long intervalMs = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnsureOnStartup() {
        return ensureOnStartup;
    }

    public void setEnsureOnStartup(boolean ensureOnStartup) {
        this.ensureOnStartup = ensureOnStartup;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }
}
