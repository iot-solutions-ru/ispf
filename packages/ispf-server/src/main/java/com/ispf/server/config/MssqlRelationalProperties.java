package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Opt-in MSSQL single-engine profile (ADR-0078 §2b). Default off. Not a Prod Yes switch.
 */
@ConfigurationProperties(prefix = "ispf.relational.mssql")
public class MssqlRelationalProperties {

    private boolean singleEngine = false;
    private boolean jdbcBufferEnabled = false;
    private String jdbcBufferDirectory = "data/mssql-jdbc-buffer";
    private long jdbcBufferMaxBytes = 268_435_456L;
    /**
     * Hard ceiling while monthly partitions are not applied (Gate C).
     * Clamp every variable retention to this many days when single-engine is on.
     */
    private int maxRetentionDays = 90;

    public boolean isSingleEngine() {
        return singleEngine;
    }

    public void setSingleEngine(boolean singleEngine) {
        this.singleEngine = singleEngine;
    }

    public boolean isJdbcBufferEnabled() {
        return jdbcBufferEnabled;
    }

    public void setJdbcBufferEnabled(boolean jdbcBufferEnabled) {
        this.jdbcBufferEnabled = jdbcBufferEnabled;
    }

    public String getJdbcBufferDirectory() {
        return jdbcBufferDirectory;
    }

    public void setJdbcBufferDirectory(String jdbcBufferDirectory) {
        this.jdbcBufferDirectory = jdbcBufferDirectory;
    }

    public long getJdbcBufferMaxBytes() {
        return jdbcBufferMaxBytes;
    }

    public void setJdbcBufferMaxBytes(long jdbcBufferMaxBytes) {
        this.jdbcBufferMaxBytes = jdbcBufferMaxBytes;
    }

    public int getMaxRetentionDays() {
        return maxRetentionDays;
    }

    public void setMaxRetentionDays(int maxRetentionDays) {
        this.maxRetentionDays = Math.max(1, maxRetentionDays);
    }
}
