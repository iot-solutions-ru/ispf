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
     * Hard ceiling while monthly SWITCH retention is not cut over (Gate C2).
     * Clamp every variable retention to this many days when single-engine is on.
     */
    private int maxRetentionDays = 90;
    /**
     * Gate C2 JDBC rollup materializer into {@code variable_sample_rollups}. Default off.
     * Not the ClickHouse analytics materializer. Not a SWITCH seal.
     */
    private boolean rollupMaterializerEnabled = false;
    /** Fixed delay between materializer ticks (ms). */
    private long rollupMaterializerTickMs = 60_000L;
    /** Catch-up chunk width in hours (walks oldest-first from the coverage cursor). */
    private int rollupMaterializerChunkHours = 24;
    /**
     * Chart-prep JDBC writer into {@code variable_sample_observed_rollups}. Default off.
     * Walks a {@code sampled_at} arrival cursor and recomputes touched {@code observed_at} buckets.
     * Aggregate cutover is {@link #observedRollupReadEnabled}.
     */
    private boolean observedRollupMaterializerEnabled = false;
    private long observedRollupMaterializerTickMs = 60_000L;
    private int observedRollupMaterializerChunkHours = 24;
    /**
     * Opt-in {@code /history/aggregate} read from {@code variable_sample_observed_rollups}
     * with raw head/tail stitch. Default off. Requires single-engine; not Prod Yes B2 / SWITCH.
     */
    private boolean observedRollupReadEnabled = false;

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

    public boolean isRollupMaterializerEnabled() {
        return rollupMaterializerEnabled;
    }

    public void setRollupMaterializerEnabled(boolean rollupMaterializerEnabled) {
        this.rollupMaterializerEnabled = rollupMaterializerEnabled;
    }

    public long getRollupMaterializerTickMs() {
        return rollupMaterializerTickMs;
    }

    public void setRollupMaterializerTickMs(long rollupMaterializerTickMs) {
        this.rollupMaterializerTickMs = Math.max(1_000L, rollupMaterializerTickMs);
    }

    public int getRollupMaterializerChunkHours() {
        return rollupMaterializerChunkHours;
    }

    public void setRollupMaterializerChunkHours(int rollupMaterializerChunkHours) {
        this.rollupMaterializerChunkHours = Math.max(1, Math.min(168, rollupMaterializerChunkHours));
    }

    public boolean isObservedRollupMaterializerEnabled() {
        return observedRollupMaterializerEnabled;
    }

    public void setObservedRollupMaterializerEnabled(boolean observedRollupMaterializerEnabled) {
        this.observedRollupMaterializerEnabled = observedRollupMaterializerEnabled;
    }

    public long getObservedRollupMaterializerTickMs() {
        return observedRollupMaterializerTickMs;
    }

    public void setObservedRollupMaterializerTickMs(long observedRollupMaterializerTickMs) {
        this.observedRollupMaterializerTickMs = Math.max(1_000L, observedRollupMaterializerTickMs);
    }

    public int getObservedRollupMaterializerChunkHours() {
        return observedRollupMaterializerChunkHours;
    }

    public void setObservedRollupMaterializerChunkHours(int observedRollupMaterializerChunkHours) {
        this.observedRollupMaterializerChunkHours = Math.max(1, Math.min(168, observedRollupMaterializerChunkHours));
    }

    public boolean isObservedRollupReadEnabled() {
        return observedRollupReadEnabled;
    }

    public void setObservedRollupReadEnabled(boolean observedRollupReadEnabled) {
        this.observedRollupReadEnabled = observedRollupReadEnabled;
    }
}
