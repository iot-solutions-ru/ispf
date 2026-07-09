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

    /**
     * When true, samples are batched and persisted on background writer threads
     * ({@link com.ispf.server.history.VariableHistoryAsyncWriter}).
     */
    private boolean asyncEnabled = true;
    private int queueCapacity = 50_000;
    private int batchSize = 1_000;
    private long flushIntervalMs = 20;
    private int writerThreads = 4;
    private boolean elasticWriterEnabled = true;
    private int writerThreadsMin = 4;
    private int writerThreadsMax = 32;
    private int elasticScaleUpQueueThreshold = 100;
    private int elasticScaleDownSteps = 6;
    private int elasticScaleCheckIntervalMs = 200;
    /**
     * When true, queue overflow merges by (path, variable, field) instead of blocking the producer thread.
     */
    private boolean overflowCoalesceEnabled = true;

    /** When true with {@code store=jdbc}, samples are written to PostgreSQL and ClickHouse (BL-116). */
    private boolean dualWriteEnabled = false;

    /**
     * Write backend: {@code jdbc} (batched JDBC insert, default), {@code jpa} (legacy {@code saveAll}),
     * {@code clickhouse} (column store; BL-40 backend), or {@code cassandra}/{@code scylla}.
     */
    private String store = "jdbc";
    private ClickHouse clickhouse = new ClickHouse();
    private CassandraStoreProperties cassandra = new CassandraStoreProperties();

    public static class ClickHouse {
        private String url = "http://localhost:8123";
        private String database = "ispf";
        private String table = "variable_samples";
        private String rollupTable = "variable_rollups";
        private String username = "default";
        private String password = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getRollupTable() {
            return rollupTable;
        }

        public void setRollupTable(String rollupTable) {
            this.rollupTable = rollupTable;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

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

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public int getWriterThreads() {
        return writerThreads;
    }

    public void setWriterThreads(int writerThreads) {
        this.writerThreads = writerThreads;
    }

    public boolean isElasticWriterEnabled() {
        return elasticWriterEnabled;
    }

    public void setElasticWriterEnabled(boolean elasticWriterEnabled) {
        this.elasticWriterEnabled = elasticWriterEnabled;
    }

    public int getWriterThreadsMin() {
        return writerThreadsMin;
    }

    public void setWriterThreadsMin(int writerThreadsMin) {
        this.writerThreadsMin = Math.max(1, writerThreadsMin);
    }

    public int getWriterThreadsMax() {
        return writerThreadsMax;
    }

    public void setWriterThreadsMax(int writerThreadsMax) {
        this.writerThreadsMax = Math.max(1, writerThreadsMax);
    }

    public int getElasticScaleUpQueueThreshold() {
        return elasticScaleUpQueueThreshold;
    }

    public void setElasticScaleUpQueueThreshold(int elasticScaleUpQueueThreshold) {
        this.elasticScaleUpQueueThreshold = Math.max(1, elasticScaleUpQueueThreshold);
    }

    public int getElasticScaleDownSteps() {
        return elasticScaleDownSteps;
    }

    public void setElasticScaleDownSteps(int elasticScaleDownSteps) {
        this.elasticScaleDownSteps = Math.max(1, elasticScaleDownSteps);
    }

    public int getElasticScaleCheckIntervalMs() {
        return elasticScaleCheckIntervalMs;
    }

    public void setElasticScaleCheckIntervalMs(int elasticScaleCheckIntervalMs) {
        this.elasticScaleCheckIntervalMs = Math.max(50, elasticScaleCheckIntervalMs);
    }

    public boolean isOverflowCoalesceEnabled() {
        return overflowCoalesceEnabled;
    }

    public void setOverflowCoalesceEnabled(boolean overflowCoalesceEnabled) {
        this.overflowCoalesceEnabled = overflowCoalesceEnabled;
    }

    public int resolvedWriterThreadsMin() {
        return elasticWriterEnabled ? writerThreadsMin : Math.max(1, writerThreads);
    }

    public int resolvedWriterThreadsMax() {
        return elasticWriterEnabled ? writerThreadsMax : Math.max(1, writerThreads);
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public ClickHouse getClickhouse() {
        return clickhouse;
    }

    public void setClickhouse(ClickHouse clickhouse) {
        this.clickhouse = clickhouse;
    }

    public CassandraStoreProperties getCassandra() {
        return cassandra;
    }

    public void setCassandra(CassandraStoreProperties cassandra) {
        this.cassandra = cassandra;
    }

    public boolean isClickHouseStore() {
        return "clickhouse".equalsIgnoreCase(store);
    }

    public boolean isCassandraStore() {
        return "cassandra".equalsIgnoreCase(store) || "scylla".equalsIgnoreCase(store);
    }

    public boolean isExternalTimeSeriesStore() {
        return isClickHouseStore() || isCassandraStore();
    }

    public boolean isDualWriteEnabled() {
        return dualWriteEnabled;
    }

    public void setDualWriteEnabled(boolean dualWriteEnabled) {
        this.dualWriteEnabled = dualWriteEnabled;
    }

    public List<String> getExcludedVariables() {
        return excludedVariables;
    }

    public void setExcludedVariables(List<String> excludedVariables) {
        this.excludedVariables = excludedVariables;
    }
}
