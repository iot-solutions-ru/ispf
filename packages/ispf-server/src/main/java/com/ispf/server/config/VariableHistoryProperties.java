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
    private int queueCapacity = 10_000;
    private int batchSize = 500;
    private long flushIntervalMs = 50;
    private int writerThreads = 4;

    /**
     * Write backend: {@code jdbc} (batched JDBC insert, default), {@code jpa} (legacy {@code saveAll}),
     * or {@code clickhouse} (column store; BL-40 backend).
     */
    private String store = "jdbc";
    private ClickHouse clickhouse = new ClickHouse();

    public static class ClickHouse {
        private String url = "http://localhost:8123";
        private String database = "ispf";
        private String table = "variable_samples";
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

    public boolean isClickHouseStore() {
        return "clickhouse".equalsIgnoreCase(store);
    }

    public List<String> getExcludedVariables() {
        return excludedVariables;
    }

    public void setExcludedVariables(List<String> excludedVariables) {
        this.excludedVariables = excludedVariables;
    }
}
