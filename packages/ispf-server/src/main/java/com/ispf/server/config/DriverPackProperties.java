package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.driver")
public class DriverPackProperties {

    /** Directory scanned for licensed driver packs (`driver-pack.json` + JAR). */
    private String packsDir = "./data/drivers";

    /** Threads for the shared driver poll scheduler. */
    private int schedulerThreads = 8;

    /**
     * When true, blocking {@link com.ispf.driver.DeviceDriver#readPoints} runs on a dedicated I/O pool
     * instead of the poll scheduler thread.
     */
    private boolean asyncPollEnabled = true;

    /** Threads for async driver poll / protocol I/O. */
    private int ioThreads = 16;

    /**
     * When true, each running driver receives a last-value-wins ingress buffer before the platform
     * telemetry pipeline (RAM-only, no persistence).
     */
    private boolean ingressBufferEnabled = true;

    private int ingressBufferThreads = 2;

    private int ingressBufferCapacity = 10_000;

    private boolean ingressBufferElasticEnabled = true;

    private int ingressBufferThreadsMin = 2;

    private int ingressBufferThreadsMax = 32;

    private int ingressBufferScaleUpQueueThreshold = 100;

    private int ingressBufferScaleDownSteps = 6;

    private int ingressBufferScaleCheckIntervalMs = 200;

    /** Default MQTT Paho callback thread pool size when not set on the device binding. */
    private int mqttCallbackThreads = 4;

    /** Default MQTT ingress FIFO capacity per device when not set on the device binding. */
    private int mqttCallbackQueueCapacity = 10_000;

    private boolean mqttCallbackElasticEnabled = true;

    private int mqttCallbackThreadsMin = 4;

    private int mqttCallbackThreadsMax = 32;

    private int mqttCallbackScaleUpQueueThreshold = 100;

    private int mqttCallbackScaleDownSteps = 6;

    private int mqttCallbackScaleCheckIntervalMs = 200;

    public String getPacksDir() {
        return packsDir;
    }

    public void setPacksDir(String packsDir) {
        this.packsDir = packsDir;
    }

    public int getSchedulerThreads() {
        return schedulerThreads;
    }

    public void setSchedulerThreads(int schedulerThreads) {
        this.schedulerThreads = Math.max(1, schedulerThreads);
    }

    public boolean isAsyncPollEnabled() {
        return asyncPollEnabled;
    }

    public void setAsyncPollEnabled(boolean asyncPollEnabled) {
        this.asyncPollEnabled = asyncPollEnabled;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public void setIoThreads(int ioThreads) {
        this.ioThreads = Math.max(1, ioThreads);
    }

    public boolean isIngressBufferEnabled() {
        return ingressBufferEnabled;
    }

    public void setIngressBufferEnabled(boolean ingressBufferEnabled) {
        this.ingressBufferEnabled = ingressBufferEnabled;
    }

    public int getIngressBufferThreads() {
        return ingressBufferThreads;
    }

    public void setIngressBufferThreads(int ingressBufferThreads) {
        this.ingressBufferThreads = Math.max(1, ingressBufferThreads);
    }

    public int getIngressBufferCapacity() {
        return ingressBufferCapacity;
    }

    public void setIngressBufferCapacity(int ingressBufferCapacity) {
        this.ingressBufferCapacity = Math.max(1, ingressBufferCapacity);
    }

    public int getMqttCallbackThreads() {
        return mqttCallbackThreads;
    }

    public void setMqttCallbackThreads(int mqttCallbackThreads) {
        this.mqttCallbackThreads = Math.max(1, mqttCallbackThreads);
    }

    public int getMqttCallbackQueueCapacity() {
        return mqttCallbackQueueCapacity;
    }

    public void setMqttCallbackQueueCapacity(int mqttCallbackQueueCapacity) {
        this.mqttCallbackQueueCapacity = Math.max(1, mqttCallbackQueueCapacity);
    }

    public boolean isIngressBufferElasticEnabled() {
        return ingressBufferElasticEnabled;
    }

    public void setIngressBufferElasticEnabled(boolean ingressBufferElasticEnabled) {
        this.ingressBufferElasticEnabled = ingressBufferElasticEnabled;
    }

    public int getIngressBufferThreadsMin() {
        return ingressBufferThreadsMin;
    }

    public void setIngressBufferThreadsMin(int ingressBufferThreadsMin) {
        this.ingressBufferThreadsMin = Math.max(1, ingressBufferThreadsMin);
    }

    public int getIngressBufferThreadsMax() {
        return ingressBufferThreadsMax;
    }

    public void setIngressBufferThreadsMax(int ingressBufferThreadsMax) {
        this.ingressBufferThreadsMax = Math.max(1, ingressBufferThreadsMax);
    }

    public int getIngressBufferScaleUpQueueThreshold() {
        return ingressBufferScaleUpQueueThreshold;
    }

    public void setIngressBufferScaleUpQueueThreshold(int ingressBufferScaleUpQueueThreshold) {
        this.ingressBufferScaleUpQueueThreshold = Math.max(1, ingressBufferScaleUpQueueThreshold);
    }

    public int getIngressBufferScaleDownSteps() {
        return ingressBufferScaleDownSteps;
    }

    public void setIngressBufferScaleDownSteps(int ingressBufferScaleDownSteps) {
        this.ingressBufferScaleDownSteps = Math.max(1, ingressBufferScaleDownSteps);
    }

    public int getIngressBufferScaleCheckIntervalMs() {
        return ingressBufferScaleCheckIntervalMs;
    }

    public void setIngressBufferScaleCheckIntervalMs(int ingressBufferScaleCheckIntervalMs) {
        this.ingressBufferScaleCheckIntervalMs = Math.max(50, ingressBufferScaleCheckIntervalMs);
    }

    public IngressElasticSettings resolvedIngressBufferElastic() {
        return new IngressElasticSettings(
                ingressBufferElasticEnabled,
                resolvedIngressBufferThreadsMin(),
                resolvedIngressBufferThreadsMax(),
                ingressBufferScaleUpQueueThreshold,
                ingressBufferScaleDownSteps,
                ingressBufferScaleCheckIntervalMs
        );
    }

    public int resolvedIngressBufferThreadsMin() {
        return ingressBufferElasticEnabled ? ingressBufferThreadsMin : Math.max(1, ingressBufferThreads);
    }

    public int resolvedIngressBufferThreadsMax() {
        return ingressBufferElasticEnabled ? ingressBufferThreadsMax : Math.max(1, ingressBufferThreads);
    }

    public boolean isMqttCallbackElasticEnabled() {
        return mqttCallbackElasticEnabled;
    }

    public void setMqttCallbackElasticEnabled(boolean mqttCallbackElasticEnabled) {
        this.mqttCallbackElasticEnabled = mqttCallbackElasticEnabled;
    }

    public int getMqttCallbackThreadsMin() {
        return mqttCallbackThreadsMin;
    }

    public void setMqttCallbackThreadsMin(int mqttCallbackThreadsMin) {
        this.mqttCallbackThreadsMin = Math.max(1, mqttCallbackThreadsMin);
    }

    public int getMqttCallbackThreadsMax() {
        return mqttCallbackThreadsMax;
    }

    public void setMqttCallbackThreadsMax(int mqttCallbackThreadsMax) {
        this.mqttCallbackThreadsMax = Math.max(1, mqttCallbackThreadsMax);
    }

    public int getMqttCallbackScaleUpQueueThreshold() {
        return mqttCallbackScaleUpQueueThreshold;
    }

    public void setMqttCallbackScaleUpQueueThreshold(int mqttCallbackScaleUpQueueThreshold) {
        this.mqttCallbackScaleUpQueueThreshold = Math.max(1, mqttCallbackScaleUpQueueThreshold);
    }

    public int getMqttCallbackScaleDownSteps() {
        return mqttCallbackScaleDownSteps;
    }

    public void setMqttCallbackScaleDownSteps(int mqttCallbackScaleDownSteps) {
        this.mqttCallbackScaleDownSteps = Math.max(1, mqttCallbackScaleDownSteps);
    }

    public int getMqttCallbackScaleCheckIntervalMs() {
        return mqttCallbackScaleCheckIntervalMs;
    }

    public void setMqttCallbackScaleCheckIntervalMs(int mqttCallbackScaleCheckIntervalMs) {
        this.mqttCallbackScaleCheckIntervalMs = Math.max(50, mqttCallbackScaleCheckIntervalMs);
    }

    public IngressElasticSettings resolvedMqttCallbackElastic() {
        return new IngressElasticSettings(
                mqttCallbackElasticEnabled,
                resolvedMqttCallbackThreadsMin(),
                resolvedMqttCallbackThreadsMax(),
                mqttCallbackScaleUpQueueThreshold,
                mqttCallbackScaleDownSteps,
                mqttCallbackScaleCheckIntervalMs
        );
    }

    public int resolvedMqttCallbackThreadsMin() {
        return mqttCallbackElasticEnabled ? mqttCallbackThreadsMin : Math.max(1, mqttCallbackThreads);
    }

    public int resolvedMqttCallbackThreadsMax() {
        return mqttCallbackElasticEnabled ? mqttCallbackThreadsMax : Math.max(1, mqttCallbackThreads);
    }
}
