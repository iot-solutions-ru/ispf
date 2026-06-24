package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.object-change")
public class ObjectChangeProperties {

    /**
     * When true, heavy {@code ObjectChangeEvent} handlers run on the {@link com.ispf.server.object.bus.ObjectChangeEventBus}.
     * When false, handlers run synchronously on the publishing thread after other listeners.
     */
    private boolean asyncEnabled = true;
    private int queueCapacity = 10_000;
    private int workerThreads = 4;
    private boolean coalesceTelemetryUpdates = true;

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

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public boolean isCoalesceTelemetryUpdates() {
        return coalesceTelemetryUpdates;
    }

    public void setCoalesceTelemetryUpdates(boolean coalesceTelemetryUpdates) {
        this.coalesceTelemetryUpdates = coalesceTelemetryUpdates;
    }
}
