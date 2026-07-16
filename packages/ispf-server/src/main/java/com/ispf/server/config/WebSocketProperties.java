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

    /**
     * Comma-separated origin patterns for {@code /ws/**} (Spring allowedOriginPatterns syntax).
     * Default is localhost-only; {@code application-local.yml} / {@code application-test.yml} use {@code *}.
     * Internet-facing deployments should set an explicit host list (or activate {@code prod} profile).
     */
    private String allowedOriginPatterns =
            "http://localhost:*,http://127.0.0.1:*,https://localhost:*,https://127.0.0.1:*";

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

    public String getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns == null
                ? "http://localhost:*,http://127.0.0.1:*,https://localhost:*,https://127.0.0.1:*"
                : allowedOriginPatterns;
    }

    public String[] resolvedAllowedOriginPatterns() {
        String raw = allowedOriginPatterns == null ? "" : allowedOriginPatterns.trim();
        if (raw.isEmpty()) {
            return new String[] {
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "https://localhost:*",
                    "https://127.0.0.1:*"
            };
        }
        if ("*".equals(raw)) {
            return new String[] {"*"};
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toArray(String[]::new);
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
