package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.runtime-telemetry")
public class RuntimeTelemetryProperties {

    private boolean enabled = true;

    private long coalesceMs = 1_000;

    /** When false, L4 publishes immediately (no pending map / coalesce-ms window). */
    private boolean coalesceEnabled = true;

    /** Fixed scheduler threads when {@link #coalesceSchedulerElastic} is false. */
    private int coalesceSchedulerThreads = 4;

    private boolean coalesceSchedulerElastic = true;

    private int coalesceSchedulerThreadsMin = 4;

    private int coalesceSchedulerThreadsMax = 32;

    private int coalesceSchedulerScaleUpThreshold = 50;

    private int coalesceSchedulerScaleDownSteps = 6;

    private int coalesceSchedulerScaleCheckIntervalMs = 200;

    /**
     * When true, driver telemetry updates are batched on an elastic ingress queue (last-value-wins per lane)
     * before {@link com.ispf.server.object.RuntimeTelemetryCoalescer}.
     */
    private boolean ingressQueueEnabled = true;

    /** Max distinct coalesce lanes held in the ingress pending map. */
    private int ingressQueueCapacity = 50_000;

    private boolean ingressElasticWorkers = true;
    private int ingressWorkerThreadsMin = 4;
    private int ingressWorkerThreadsMax = 32;
    private int ingressScaleUpQueueThreshold = 100;
    private int ingressScaleDownSteps = 6;
    private int ingressScaleCheckIntervalMs = 200;
    private int ingressDrainBatchSize = 512;

    /** When true, L3 holds last-value-wins per lane; false = FIFO queue (no coalesce drop). */
    private boolean ingressQueueCoalesceEnabled = true;

    /**
     * When true, {@code TELEMETRY_ONLY} devices with historian-only interest skip the object-change bus
     * and enqueue samples directly (lower latency, higher throughput).
     */
    private boolean fastHistorianPath = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCoalesceMs() {
        return coalesceMs;
    }

    public void setCoalesceMs(long coalesceMs) {
        this.coalesceMs = coalesceMs;
    }

    public boolean isCoalesceEnabled() {
        return coalesceEnabled;
    }

    public void setCoalesceEnabled(boolean coalesceEnabled) {
        this.coalesceEnabled = coalesceEnabled;
    }

    public int getCoalesceSchedulerThreads() {
        return coalesceSchedulerThreads;
    }

    public void setCoalesceSchedulerThreads(int coalesceSchedulerThreads) {
        this.coalesceSchedulerThreads = Math.max(1, coalesceSchedulerThreads);
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

    public int resolvedCoalesceSchedulerThreadsMin() {
        return coalesceSchedulerElastic ? coalesceSchedulerThreadsMin : coalesceSchedulerThreads;
    }

    public int resolvedCoalesceSchedulerThreadsMax() {
        return coalesceSchedulerElastic ? coalesceSchedulerThreadsMax : coalesceSchedulerThreads;
    }

    public boolean isIngressQueueEnabled() {
        return ingressQueueEnabled;
    }

    public void setIngressQueueEnabled(boolean ingressQueueEnabled) {
        this.ingressQueueEnabled = ingressQueueEnabled;
    }

    public int getIngressQueueCapacity() {
        return ingressQueueCapacity;
    }

    public void setIngressQueueCapacity(int ingressQueueCapacity) {
        this.ingressQueueCapacity = Math.max(1_000, ingressQueueCapacity);
    }

    public boolean isIngressElasticWorkers() {
        return ingressElasticWorkers;
    }

    public void setIngressElasticWorkers(boolean ingressElasticWorkers) {
        this.ingressElasticWorkers = ingressElasticWorkers;
    }

    public int getIngressWorkerThreadsMin() {
        return ingressWorkerThreadsMin;
    }

    public void setIngressWorkerThreadsMin(int ingressWorkerThreadsMin) {
        this.ingressWorkerThreadsMin = Math.max(1, ingressWorkerThreadsMin);
    }

    public int getIngressWorkerThreadsMax() {
        return ingressWorkerThreadsMax;
    }

    public void setIngressWorkerThreadsMax(int ingressWorkerThreadsMax) {
        this.ingressWorkerThreadsMax = Math.max(1, ingressWorkerThreadsMax);
    }

    public int getIngressScaleUpQueueThreshold() {
        return ingressScaleUpQueueThreshold;
    }

    public void setIngressScaleUpQueueThreshold(int ingressScaleUpQueueThreshold) {
        this.ingressScaleUpQueueThreshold = Math.max(1, ingressScaleUpQueueThreshold);
    }

    public int getIngressScaleDownSteps() {
        return ingressScaleDownSteps;
    }

    public void setIngressScaleDownSteps(int ingressScaleDownSteps) {
        this.ingressScaleDownSteps = Math.max(1, ingressScaleDownSteps);
    }

    public int getIngressScaleCheckIntervalMs() {
        return ingressScaleCheckIntervalMs;
    }

    public void setIngressScaleCheckIntervalMs(int ingressScaleCheckIntervalMs) {
        this.ingressScaleCheckIntervalMs = Math.max(50, ingressScaleCheckIntervalMs);
    }

    public int getIngressDrainBatchSize() {
        return ingressDrainBatchSize;
    }

    public void setIngressDrainBatchSize(int ingressDrainBatchSize) {
        this.ingressDrainBatchSize = Math.max(16, ingressDrainBatchSize);
    }

    public boolean isIngressQueueCoalesceEnabled() {
        return ingressQueueCoalesceEnabled;
    }

    public void setIngressQueueCoalesceEnabled(boolean ingressQueueCoalesceEnabled) {
        this.ingressQueueCoalesceEnabled = ingressQueueCoalesceEnabled;
    }

    public boolean isFastHistorianPath() {
        return fastHistorianPath;
    }

    public void setFastHistorianPath(boolean fastHistorianPath) {
        this.fastHistorianPath = fastHistorianPath;
    }

    public int resolvedIngressWorkerThreadsMin() {
        return ingressElasticWorkers ? ingressWorkerThreadsMin : ingressWorkerThreadsMax;
    }

    public int resolvedIngressWorkerThreadsMax() {
        return ingressElasticWorkers ? ingressWorkerThreadsMax : ingressWorkerThreadsMin;
    }
}
