package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.event-journal")
public class EventJournalProperties {

    /**
     * When true, {@link com.ispf.server.event.EventJournalAsyncWriter} persists events on a background thread.
     * When false, events are written synchronously on the fire path.
     */
    private boolean asyncEnabled = true;
    private int queueCapacity = 10_000;
    private int batchSize = 100;
    private long flushIntervalMs = 250;
    private int recentCacheSize = 500;

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

    public int getRecentCacheSize() {
        return recentCacheSize;
    }

    public void setRecentCacheSize(int recentCacheSize) {
        this.recentCacheSize = recentCacheSize;
    }
}
