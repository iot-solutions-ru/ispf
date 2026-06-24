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

    /**
     * When true, telemetry handlers and automation handlers use separate queues and worker pools.
     * When false, all handlers share {@link #queueCapacity} and {@link #workerThreads}.
     */
    private boolean splitLanesEnabled = true;
    private int telemetryQueueCapacity = 10_000;
    private int automationQueueCapacity = 10_000;
    private int telemetryWorkerThreads = 2;
    private int automationWorkerThreads = 4;

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

    public boolean isSplitLanesEnabled() {
        return splitLanesEnabled;
    }

    public void setSplitLanesEnabled(boolean splitLanesEnabled) {
        this.splitLanesEnabled = splitLanesEnabled;
    }

    public int getTelemetryQueueCapacity() {
        return telemetryQueueCapacity;
    }

    public void setTelemetryQueueCapacity(int telemetryQueueCapacity) {
        this.telemetryQueueCapacity = telemetryQueueCapacity;
    }

    public int getAutomationQueueCapacity() {
        return automationQueueCapacity;
    }

    public void setAutomationQueueCapacity(int automationQueueCapacity) {
        this.automationQueueCapacity = automationQueueCapacity;
    }

    public int getTelemetryWorkerThreads() {
        return telemetryWorkerThreads;
    }

    public void setTelemetryWorkerThreads(int telemetryWorkerThreads) {
        this.telemetryWorkerThreads = telemetryWorkerThreads;
    }

    public int getAutomationWorkerThreads() {
        return automationWorkerThreads;
    }

    public void setAutomationWorkerThreads(int automationWorkerThreads) {
        this.automationWorkerThreads = automationWorkerThreads;
    }
}
