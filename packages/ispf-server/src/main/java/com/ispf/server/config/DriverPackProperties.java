package com.ispf.server.config;

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

    /** Default MQTT Paho callback thread pool size when not set on the device binding. */
    private int mqttCallbackThreads = 4;

    /** Default MQTT ingress FIFO capacity per device when not set on the device binding. */
    private int mqttCallbackQueueCapacity = 10_000;

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
}
