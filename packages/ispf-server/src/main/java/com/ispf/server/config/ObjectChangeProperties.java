package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
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
    private boolean coalesceTelemetryUpdates = false;

    /**
     * When true, telemetry handlers and automation handlers use separate queues and worker pools.
     * When false, all handlers share {@link #queueCapacity} and {@link #workerThreads}.
     */
    private boolean splitLanesEnabled = true;
    /**
     * ADR-0024: publish variable change events only when historian/bindings/alerts/workflows subscribe.
     */
    private boolean demandDrivenPublication = true;
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

    /** Scheduled threads for telemetry bus coalesce flush windows (fixed when {@link #coalesceSchedulerElastic} is false). */
    private int coalesceSchedulerThreads = 2;

    private boolean coalesceSchedulerElastic = true;

    private int coalesceSchedulerThreadsMin = 2;

    private int coalesceSchedulerThreadsMax = 16;

    private int coalesceSchedulerScaleUpThreshold = 20;

    private int coalesceSchedulerScaleDownSteps = 6;

    private int coalesceSchedulerScaleCheckIntervalMs = 200;

    /** Scheduled threads shared by elastic worker scale checks on all lanes (fixed when {@link #scaleSchedulerElastic} is false). */
    private int scaleSchedulerThreads = 2;

    private boolean scaleSchedulerElastic = true;

    private int scaleSchedulerThreadsMin = 1;

    private int scaleSchedulerThreadsMax = 8;

    private int scaleSchedulerScaleUpThreshold = 2;

    private int scaleSchedulerScaleDownSteps = 6;

    private int scaleSchedulerScaleCheckIntervalMs = 500;

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

    public int getCoalesceSchedulerThreads() {
        return coalesceSchedulerThreads;
    }

    public void setCoalesceSchedulerThreads(int coalesceSchedulerThreads) {
        this.coalesceSchedulerThreads = Math.max(1, coalesceSchedulerThreads);
    }

    public int getScaleSchedulerThreads() {
        return scaleSchedulerThreads;
    }

    public void setScaleSchedulerThreads(int scaleSchedulerThreads) {
        this.scaleSchedulerThreads = Math.max(1, scaleSchedulerThreads);
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

    public boolean isDemandDrivenPublication() {
        return demandDrivenPublication;
    }

    public void setDemandDrivenPublication(boolean demandDrivenPublication) {
        this.demandDrivenPublication = demandDrivenPublication;
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

    public boolean isCoalesceSchedulerElastic() {
        return coalesceSchedulerElastic;
    }

    public void setCoalesceSchedulerElastic(boolean coalesceSchedulerElastic) {
        this.coalesceSchedulerElastic = coalesceSchedulerElastic;
    }

    public int getCoalesceSchedulerThreadsMin() {
        return coalesceSchedulerThreadsMin;
    }

    public void setCoalesceSchedulerThreadsMin(int coalesceSchedulerThreadsMin) {
        this.coalesceSchedulerThreadsMin = Math.max(1, coalesceSchedulerThreadsMin);
    }

    public int getCoalesceSchedulerThreadsMax() {
        return coalesceSchedulerThreadsMax;
    }

    public void setCoalesceSchedulerThreadsMax(int coalesceSchedulerThreadsMax) {
        this.coalesceSchedulerThreadsMax = Math.max(1, coalesceSchedulerThreadsMax);
    }

    public int getCoalesceSchedulerScaleUpThreshold() {
        return coalesceSchedulerScaleUpThreshold;
    }

    public void setCoalesceSchedulerScaleUpThreshold(int coalesceSchedulerScaleUpThreshold) {
        this.coalesceSchedulerScaleUpThreshold = Math.max(1, coalesceSchedulerScaleUpThreshold);
    }

    public int getCoalesceSchedulerScaleDownSteps() {
        return coalesceSchedulerScaleDownSteps;
    }

    public void setCoalesceSchedulerScaleDownSteps(int coalesceSchedulerScaleDownSteps) {
        this.coalesceSchedulerScaleDownSteps = Math.max(1, coalesceSchedulerScaleDownSteps);
    }

    public int getCoalesceSchedulerScaleCheckIntervalMs() {
        return coalesceSchedulerScaleCheckIntervalMs;
    }

    public void setCoalesceSchedulerScaleCheckIntervalMs(int coalesceSchedulerScaleCheckIntervalMs) {
        this.coalesceSchedulerScaleCheckIntervalMs = Math.max(50, coalesceSchedulerScaleCheckIntervalMs);
    }

    public IngressElasticSettings resolvedCoalesceSchedulerElastic() {
        return new IngressElasticSettings(
                coalesceSchedulerElastic,
                coalesceSchedulerElastic ? coalesceSchedulerThreadsMin : coalesceSchedulerThreads,
                coalesceSchedulerElastic ? coalesceSchedulerThreadsMax : coalesceSchedulerThreads,
                coalesceSchedulerScaleUpThreshold,
                coalesceSchedulerScaleDownSteps,
                coalesceSchedulerScaleCheckIntervalMs
        );
    }

    public boolean isScaleSchedulerElastic() {
        return scaleSchedulerElastic;
    }

    public void setScaleSchedulerElastic(boolean scaleSchedulerElastic) {
        this.scaleSchedulerElastic = scaleSchedulerElastic;
    }

    public int getScaleSchedulerThreadsMin() {
        return scaleSchedulerThreadsMin;
    }

    public void setScaleSchedulerThreadsMin(int scaleSchedulerThreadsMin) {
        this.scaleSchedulerThreadsMin = Math.max(1, scaleSchedulerThreadsMin);
    }

    public int getScaleSchedulerThreadsMax() {
        return scaleSchedulerThreadsMax;
    }

    public void setScaleSchedulerThreadsMax(int scaleSchedulerThreadsMax) {
        this.scaleSchedulerThreadsMax = Math.max(1, scaleSchedulerThreadsMax);
    }

    public int getScaleSchedulerScaleUpThreshold() {
        return scaleSchedulerScaleUpThreshold;
    }

    public void setScaleSchedulerScaleUpThreshold(int scaleSchedulerScaleUpThreshold) {
        this.scaleSchedulerScaleUpThreshold = Math.max(1, scaleSchedulerScaleUpThreshold);
    }

    public int getScaleSchedulerScaleDownSteps() {
        return scaleSchedulerScaleDownSteps;
    }

    public void setScaleSchedulerScaleDownSteps(int scaleSchedulerScaleDownSteps) {
        this.scaleSchedulerScaleDownSteps = Math.max(1, scaleSchedulerScaleDownSteps);
    }

    public int getScaleSchedulerScaleCheckIntervalMs() {
        return scaleSchedulerScaleCheckIntervalMs;
    }

    public void setScaleSchedulerScaleCheckIntervalMs(int scaleSchedulerScaleCheckIntervalMs) {
        this.scaleSchedulerScaleCheckIntervalMs = Math.max(50, scaleSchedulerScaleCheckIntervalMs);
    }

    public IngressElasticSettings resolvedScaleSchedulerElastic() {
        return new IngressElasticSettings(
                scaleSchedulerElastic,
                scaleSchedulerElastic ? scaleSchedulerThreadsMin : scaleSchedulerThreads,
                scaleSchedulerElastic ? scaleSchedulerThreadsMax : scaleSchedulerThreads,
                scaleSchedulerScaleUpThreshold,
                scaleSchedulerScaleDownSteps,
                scaleSchedulerScaleCheckIntervalMs
        );
    }
}
