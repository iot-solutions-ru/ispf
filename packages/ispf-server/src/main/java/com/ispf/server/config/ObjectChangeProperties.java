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

    /** Elastic worker pool: scale between min/max based on queue depth. */
    private boolean elasticWorkersEnabled = true;
    private int elasticScaleUpQueueThreshold = 50;
    private int elasticScaleDownSteps = 6;
    private int elasticScaleCheckIntervalMs = 500;
    private int workerThreadsMin = 2;
    private int workerThreadsMax = 16;
    private int telemetryWorkerThreadsMin = 1;
    private int telemetryWorkerThreadsMax = 8;
    private int automationWorkerThreadsMin = 2;
    private int automationWorkerThreadsMax = 16;

    public boolean isElasticWorkersEnabled() {
        return elasticWorkersEnabled;
    }

    public void setElasticWorkersEnabled(boolean elasticWorkersEnabled) {
        this.elasticWorkersEnabled = elasticWorkersEnabled;
    }

    public int getElasticScaleUpQueueThreshold() {
        return elasticScaleUpQueueThreshold;
    }

    public void setElasticScaleUpQueueThreshold(int elasticScaleUpQueueThreshold) {
        this.elasticScaleUpQueueThreshold = elasticScaleUpQueueThreshold;
    }

    public int getElasticScaleDownSteps() {
        return elasticScaleDownSteps;
    }

    public void setElasticScaleDownSteps(int elasticScaleDownSteps) {
        this.elasticScaleDownSteps = elasticScaleDownSteps;
    }

    public int getElasticScaleCheckIntervalMs() {
        return elasticScaleCheckIntervalMs;
    }

    public void setElasticScaleCheckIntervalMs(int elasticScaleCheckIntervalMs) {
        this.elasticScaleCheckIntervalMs = elasticScaleCheckIntervalMs;
    }

    public int getWorkerThreadsMin() {
        return workerThreadsMin;
    }

    public void setWorkerThreadsMin(int workerThreadsMin) {
        this.workerThreadsMin = workerThreadsMin;
    }

    public int getWorkerThreadsMax() {
        return workerThreadsMax;
    }

    public void setWorkerThreadsMax(int workerThreadsMax) {
        this.workerThreadsMax = workerThreadsMax;
    }

    public int getTelemetryWorkerThreadsMin() {
        return telemetryWorkerThreadsMin;
    }

    public void setTelemetryWorkerThreadsMin(int telemetryWorkerThreadsMin) {
        this.telemetryWorkerThreadsMin = telemetryWorkerThreadsMin;
    }

    public int getTelemetryWorkerThreadsMax() {
        return telemetryWorkerThreadsMax;
    }

    public void setTelemetryWorkerThreadsMax(int telemetryWorkerThreadsMax) {
        this.telemetryWorkerThreadsMax = telemetryWorkerThreadsMax;
    }

    public int getAutomationWorkerThreadsMin() {
        return automationWorkerThreadsMin;
    }

    public void setAutomationWorkerThreadsMin(int automationWorkerThreadsMin) {
        this.automationWorkerThreadsMin = automationWorkerThreadsMin;
    }

    public int getAutomationWorkerThreadsMax() {
        return automationWorkerThreadsMax;
    }

    public void setAutomationWorkerThreadsMax(int automationWorkerThreadsMax) {
        this.automationWorkerThreadsMax = automationWorkerThreadsMax;
    }

    public int resolvedWorkerThreadsMin() {
        return elasticWorkersEnabled ? workerThreadsMin : workerThreads;
    }

    public int resolvedWorkerThreadsMax() {
        return elasticWorkersEnabled ? workerThreadsMax : workerThreads;
    }

    public int resolvedTelemetryWorkerThreadsMin() {
        return elasticWorkersEnabled ? telemetryWorkerThreadsMin : telemetryWorkerThreads;
    }

    public int resolvedTelemetryWorkerThreadsMax() {
        return elasticWorkersEnabled ? telemetryWorkerThreadsMax : telemetryWorkerThreads;
    }

    public int resolvedAutomationWorkerThreadsMin() {
        return elasticWorkersEnabled ? automationWorkerThreadsMin : automationWorkerThreads;
    }

    public int resolvedAutomationWorkerThreadsMax() {
        return elasticWorkersEnabled ? automationWorkerThreadsMax : automationWorkerThreads;
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
}
