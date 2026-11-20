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
}
