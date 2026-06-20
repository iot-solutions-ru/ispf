package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ispf.variable-history")
public class VariableHistoryProperties {

    /** Master switch for recording and querying samples. */
    private boolean enabled = true;

    /** Minimum interval between stored samples for the same (path, variable, field). */
    private long minIntervalMs = 5_000;

    /** Delete samples older than this many days when variable has no explicit retention. */
    private int retentionDays = 90;

    /** Variable names never historized (exact match), even if historyEnabled is true. */
    private List<String> excludedVariables = new ArrayList<>(List.of(
            "layout",
            "bpmnXml",
            "triggerJson",
            "instanceState",
            "driverConfigJson",
            "driverPointMappingsJson",
            "driverId",
            "driverStatus",
            "driverPollIntervalMs"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }

    public void setMinIntervalMs(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public List<String> getExcludedVariables() {
        return excludedVariables;
    }

    public void setExcludedVariables(List<String> excludedVariables) {
        this.excludedVariables = excludedVariables;
    }
}
