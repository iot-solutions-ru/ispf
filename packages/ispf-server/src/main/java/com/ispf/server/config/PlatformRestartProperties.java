package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.platform.restart")
public class PlatformRestartProperties {

    /** Allow System → Settings to schedule a process restart (disabled in tests). */
    private boolean enabled = true;

    /** systemd unit name without {@code .service}. */
    private String unit = "ispf-server";

    /** Delay before systemd restart / JVM exit so the HTTP response can flush. */
    private long delayMs = 2000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
