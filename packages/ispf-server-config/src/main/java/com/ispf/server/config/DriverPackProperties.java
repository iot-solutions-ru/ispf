package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.driver")
public class DriverPackProperties {

    /** Directory scanned for licensed driver packs (`driver-pack.json` + JAR). */
    private String packsDir = "./data/drivers";

    /** Threads for the shared driver poll scheduler (fixed when {@link #schedulerThreadsElasticEnabled} is false). */
    private int schedulerThreads = 8;

    private boolean schedulerThreadsElasticEnabled = true;

    private int schedulerThreadsMin = 4;

    private int schedulerThreadsMax = 32;

    private int schedulerScaleUpThreshold = 4;

    private int schedulerScaleDownSteps = 6;

    private int schedulerScaleCheckIntervalMs = 200;

    /**
     * When true, blocking {@link com.ispf.driver.DeviceDriver#readPoints} runs on a dedicated I/O pool
     * instead of the poll scheduler thread.
     */
    private boolean asyncPollEnabled = true;

    /** Threads for async driver poll / protocol I/O (fixed when {@link #ioThreadsElasticEnabled} is false). */
    private int ioThreads = 16;

    private boolean ioThreadsElasticEnabled = true;

    private int ioThreadsMin = 4;

    private int ioThreadsMax = 32;

    private int ioScaleUpQueueThreshold = 50;

    private int ioScaleDownSteps = 6;

    private int ioScaleCheckIntervalMs = 200;

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

    /** Default MQTT driver L0 ingress coalesce when not set on device binding ({@code ingressCoalesceEnabled}). */
    private boolean mqttIngressCoalesceEnabled = true;

    /** When false, skip {@link com.ispf.server.driver.DriverRuntimeService#startConfiguredDrivers()} on boot. */
    private boolean autoStartOnBoot = true;

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

    public boolean isSchedulerThreadsElasticEnabled() {
        return schedulerThreadsElasticEnabled;
    }

    public void setSchedulerThreadsElasticEnabled(boolean schedulerThreadsElasticEnabled) {
        this.schedulerThreadsElasticEnabled = schedulerThreadsElasticEnabled;
    }

    public int getSchedulerThreadsMin() {
        return schedulerThreadsMin;
    }

    public void setSchedulerThreadsMin(int schedulerThreadsMin) {
        this.schedulerThreadsMin = Math.max(1, schedulerThreadsMin);
    }

    public int getSchedulerThreadsMax() {
        return schedulerThreadsMax;
    }

    public void setSchedulerThreadsMax(int schedulerThreadsMax) {
        this.schedulerThreadsMax = Math.max(1, schedulerThreadsMax);
    }

    public int getSchedulerScaleUpThreshold() {
        return schedulerScaleUpThreshold;
    }

    public void setSchedulerScaleUpThreshold(int schedulerScaleUpThreshold) {
        this.schedulerScaleUpThreshold = Math.max(1, schedulerScaleUpThreshold);
    }

    public int getSchedulerScaleDownSteps() {
        return schedulerScaleDownSteps;
    }

    public void setSchedulerScaleDownSteps(int schedulerScaleDownSteps) {
        this.schedulerScaleDownSteps = Math.max(1, schedulerScaleDownSteps);
    }

    public int getSchedulerScaleCheckIntervalMs() {
        return schedulerScaleCheckIntervalMs;
    }

    public void setSchedulerScaleCheckIntervalMs(int schedulerScaleCheckIntervalMs) {
        this.schedulerScaleCheckIntervalMs = Math.max(50, schedulerScaleCheckIntervalMs);
    }

    public IngressElasticSettings resolvedSchedulerElastic() {
        return new IngressElasticSettings(
                schedulerThreadsElasticEnabled,
                schedulerThreadsElasticEnabled ? schedulerThreadsMin : schedulerThreads,
                schedulerThreadsElasticEnabled ? schedulerThreadsMax : schedulerThreads,
                schedulerScaleUpThreshold,
                schedulerScaleDownSteps,
                schedulerScaleCheckIntervalMs
        );
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

    public boolean isIoThreadsElasticEnabled() {
        return ioThreadsElasticEnabled;
    }

    public void setIoThreadsElasticEnabled(boolean ioThreadsElasticEnabled) {
        this.ioThreadsElasticEnabled = ioThreadsElasticEnabled;
    }

    public int getIoThreadsMin() {
        return ioThreadsMin;
    }

    public void setIoThreadsMin(int ioThreadsMin) {
        this.ioThreadsMin = Math.max(1, ioThreadsMin);
    }

    public int getIoThreadsMax() {
        return ioThreadsMax;
    }

    public void setIoThreadsMax(int ioThreadsMax) {
        this.ioThreadsMax = Math.max(1, ioThreadsMax);
    }

    public int getIoScaleUpQueueThreshold() {
        return ioScaleUpQueueThreshold;
    }

    public void setIoScaleUpQueueThreshold(int ioScaleUpQueueThreshold) {
        this.ioScaleUpQueueThreshold = Math.max(1, ioScaleUpQueueThreshold);
    }

    public int getIoScaleDownSteps() {
        return ioScaleDownSteps;
    }

    public void setIoScaleDownSteps(int ioScaleDownSteps) {
        this.ioScaleDownSteps = Math.max(1, ioScaleDownSteps);
    }

    public int getIoScaleCheckIntervalMs() {
        return ioScaleCheckIntervalMs;
    }

    public void setIoScaleCheckIntervalMs(int ioScaleCheckIntervalMs) {
        this.ioScaleCheckIntervalMs = Math.max(50, ioScaleCheckIntervalMs);
    }

    public int resolvedIoThreadsMin() {
        return ioThreadsElasticEnabled ? ioThreadsMin : ioThreads;
    }

    public int resolvedIoThreadsMax() {
        return ioThreadsElasticEnabled ? ioThreadsMax : ioThreads;
    }

    public IngressElasticSettings resolvedIoElastic() {
        return new IngressElasticSettings(
                ioThreadsElasticEnabled,
                resolvedIoThreadsMin(),
                resolvedIoThreadsMax(),
                ioScaleUpQueueThreshold,
                ioScaleDownSteps,
                ioScaleCheckIntervalMs
        );
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

    public boolean isMqttIngressCoalesceEnabled() {
        return mqttIngressCoalesceEnabled;
    }

    public void setMqttIngressCoalesceEnabled(boolean mqttIngressCoalesceEnabled) {
        this.mqttIngressCoalesceEnabled = mqttIngressCoalesceEnabled;
    }

    public boolean isAutoStartOnBoot() {
        return autoStartOnBoot;
    }

    public void setAutoStartOnBoot(boolean autoStartOnBoot) {
        this.autoStartOnBoot = autoStartOnBoot;
    }
}
