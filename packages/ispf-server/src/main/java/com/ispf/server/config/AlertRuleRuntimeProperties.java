package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ispf.alert-rule.runtime")
public class AlertRuleRuntimeProperties {

    private boolean flushEnabled = true;
    private long flushIntervalMs = 30_000;

    public boolean isFlushEnabled() {
        return flushEnabled;
    }

    public void setFlushEnabled(boolean flushEnabled) {
        this.flushEnabled = flushEnabled;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }
}
