package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.mqtt-gateway")
public class MqttGatewayProperties {

    /** Fixed worker count when {@link #ingressDispatchElasticEnabled} is false. */
    private int ingressDispatchThreads = 8;

    private boolean ingressDispatchElasticEnabled = true;

    private int ingressDispatchThreadsMin = 4;

    private int ingressDispatchThreadsMax = 32;

    /** Max distinct gateway/topic lanes held before synchronous fallback dispatch. */
    private int ingressDispatchQueueCapacity = 50_000;

    private int ingressDispatchScaleUpQueueThreshold = 50;

    private int ingressDispatchScaleDownSteps = 6;

    private int ingressDispatchScaleCheckIntervalMs = 200;

    /** Last-value-wins per topic/payload lane before worker dispatch; false = FIFO (no drop under load). */
    private boolean ingressDispatchCoalesceEnabled = true;

    /** When true, parallel gateway {@code lastIngress} skips L3 ingress queue (direct L5 dispatch). */
    private boolean ingressBypassL3Queue = false;

    public int getIngressDispatchThreads() {
        return ingressDispatchThreads;
    }

    public void setIngressDispatchThreads(int ingressDispatchThreads) {
        this.ingressDispatchThreads = Math.max(1, ingressDispatchThreads);
    }

    public boolean isIngressDispatchElasticEnabled() {
        return ingressDispatchElasticEnabled;
    }

    public void setIngressDispatchElasticEnabled(boolean ingressDispatchElasticEnabled) {
        this.ingressDispatchElasticEnabled = ingressDispatchElasticEnabled;
    }

    public int getIngressDispatchThreadsMin() {
        return ingressDispatchThreadsMin;
    }

    public void setIngressDispatchThreadsMin(int ingressDispatchThreadsMin) {
        this.ingressDispatchThreadsMin = Math.max(1, ingressDispatchThreadsMin);
    }

    public int getIngressDispatchThreadsMax() {
        return ingressDispatchThreadsMax;
    }

    public void setIngressDispatchThreadsMax(int ingressDispatchThreadsMax) {
        this.ingressDispatchThreadsMax = Math.max(1, ingressDispatchThreadsMax);
    }

    public int getIngressDispatchQueueCapacity() {
        return ingressDispatchQueueCapacity;
    }

    public void setIngressDispatchQueueCapacity(int ingressDispatchQueueCapacity) {
        this.ingressDispatchQueueCapacity = Math.max(1_000, ingressDispatchQueueCapacity);
    }

    public int getIngressDispatchScaleUpQueueThreshold() {
        return ingressDispatchScaleUpQueueThreshold;
    }

    public void setIngressDispatchScaleUpQueueThreshold(int ingressDispatchScaleUpQueueThreshold) {
        this.ingressDispatchScaleUpQueueThreshold = Math.max(1, ingressDispatchScaleUpQueueThreshold);
    }

    public int getIngressDispatchScaleDownSteps() {
        return ingressDispatchScaleDownSteps;
    }

    public void setIngressDispatchScaleDownSteps(int ingressDispatchScaleDownSteps) {
        this.ingressDispatchScaleDownSteps = Math.max(1, ingressDispatchScaleDownSteps);
    }

    public int getIngressDispatchScaleCheckIntervalMs() {
        return ingressDispatchScaleCheckIntervalMs;
    }

    public void setIngressDispatchScaleCheckIntervalMs(int ingressDispatchScaleCheckIntervalMs) {
        this.ingressDispatchScaleCheckIntervalMs = Math.max(50, ingressDispatchScaleCheckIntervalMs);
    }

    public boolean isIngressDispatchCoalesceEnabled() {
        return ingressDispatchCoalesceEnabled;
    }

    public void setIngressDispatchCoalesceEnabled(boolean ingressDispatchCoalesceEnabled) {
        this.ingressDispatchCoalesceEnabled = ingressDispatchCoalesceEnabled;
    }

    public boolean isIngressBypassL3Queue() {
        return ingressBypassL3Queue;
    }

    public void setIngressBypassL3Queue(boolean ingressBypassL3Queue) {
        this.ingressBypassL3Queue = ingressBypassL3Queue;
    }

    public int resolvedIngressDispatchThreadsMin() {
        return ingressDispatchElasticEnabled ? ingressDispatchThreadsMin : ingressDispatchThreads;
    }

    public int resolvedIngressDispatchThreadsMax() {
        return ingressDispatchElasticEnabled ? ingressDispatchThreadsMax : ingressDispatchThreads;
    }

    public IngressElasticSettings resolvedIngressDispatchElastic() {
        return new IngressElasticSettings(
                ingressDispatchElasticEnabled,
                resolvedIngressDispatchThreadsMin(),
                resolvedIngressDispatchThreadsMax(),
                ingressDispatchScaleUpQueueThreshold,
                ingressDispatchScaleDownSteps,
                ingressDispatchScaleCheckIntervalMs
        );
    }
}
