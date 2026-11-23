package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Opt-in MySQL single-engine profile (ADR-0079 § Amendment 5, B2). Default off. MySQL Community
 * Server 8.4.x only. It is also the only MySQL profile that may run {@code ispf.cluster.enabled}
 * (A-A, § Amendment 6).
 */
@ConfigurationProperties(prefix = "ispf.relational.mysql")
public class MysqlRelationalProperties {

    private boolean singleEngine = false;
    /**
     * Hard ceiling for historian retention in single-engine mode: MySQL B2 retention is application
     * {@code DELETE}, with no partition drop behind it.
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
