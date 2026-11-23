package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Opt-in Oracle single-engine profile (ADR-0080 B2). Default off. Oracle Database Free 23.26.x
 * cell; cluster stays refused in B2 (A-A is a later train).
 */
@ConfigurationProperties(prefix = "ispf.relational.oracle")
public class OracleRelationalProperties {

    private boolean singleEngine = false;
    /**
     * Hard ceiling for historian retention in single-engine mode: Oracle B2 retention is application
     * {@code DELETE}, with no partition drop behind it. Size retention against Free's 12 GB user-data
     * cap when that is the cell.
     */
    private int maxRetentionDays = 90;

    public boolean isSingleEngine() {
        return singleEngine;
    }

    public void setSingleEngine(boolean singleEngine) {
        this.singleEngine = singleEngine;
    }

    public int getMaxRetentionDays() {
        return maxRetentionDays;
    }

    public void setMaxRetentionDays(int maxRetentionDays) {
        this.maxRetentionDays = Math.max(1, maxRetentionDays);
    }
}
