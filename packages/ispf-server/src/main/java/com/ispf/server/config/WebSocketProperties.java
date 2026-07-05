package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.websocket")
public class WebSocketProperties {

    /** Fixed send workers when {@link #sendElasticEnabled} is false. */
    private int sendThreads = 2;

    private boolean sendElasticEnabled = true;

    private int sendThreadsMin = 2;

    private int sendThreadsMax = 32;

    private int sendScaleUpQueueThreshold = 50;

    private int sendScaleDownSteps = 6;

    private int sendScaleCheckIntervalMs = 200;

    public int getSendThreads() {
        return sendThreads;
    }

    public void setSendThreads(int sendThreads) {
        this.sendThreads = Math.max(1, sendThreads);
    }

    public boolean isSendElasticEnabled() {
        return sendElasticEnabled;
    }

    public void setSendElasticEnabled(boolean sendElasticEnabled) {
        this.sendElasticEnabled = sendElasticEnabled;
    }

    public int getSendThreadsMin() {
        return sendThreadsMin;
    }

    public void setSendThreadsMin(int sendThreadsMin) {
        this.sendThreadsMin = Math.max(1, sendThreadsMin);
    }

    public int getSendThreadsMax() {
        return sendThreadsMax;
    }

    public void setSendThreadsMax(int sendThreadsMax) {
        this.sendThreadsMax = Math.max(1, sendThreadsMax);
    }

    public int getSendScaleUpQueueThreshold() {
        return sendScaleUpQueueThreshold;
    }

    public void setSendScaleUpQueueThreshold(int sendScaleUpQueueThreshold) {
        this.sendScaleUpQueueThreshold = Math.max(1, sendScaleUpQueueThreshold);
    }

    public int getSendScaleDownSteps() {
        return sendScaleDownSteps;
    }

    public void setSendScaleDownSteps(int sendScaleDownSteps) {
        this.sendScaleDownSteps = Math.max(1, sendScaleDownSteps);
    }

    public int getSendScaleCheckIntervalMs() {
        return sendScaleCheckIntervalMs;
    }

    public void setSendScaleCheckIntervalMs(int sendScaleCheckIntervalMs) {
        this.sendScaleCheckIntervalMs = Math.max(50, sendScaleCheckIntervalMs);
    }

    public IngressElasticSettings resolvedSendElastic() {
        return new IngressElasticSettings(
                sendElasticEnabled,
                sendElasticEnabled ? sendThreadsMin : sendThreads,
                sendElasticEnabled ? sendThreadsMax : sendThreads,
                sendScaleUpQueueThreshold,
                sendScaleDownSteps,
                sendScaleCheckIntervalMs
        );
    }
}
