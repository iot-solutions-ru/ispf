package com.ispf.driver.ingress;

import java.util.Map;

/** Shared ingress tuning keys for push drivers and server-side driver bridges. */
public final class DriverIngress {

    public static final String CALLBACK_THREADS = "callbackThreads";
    public static final String CALLBACK_THREADS_MIN = "callbackThreadsMin";
    public static final String CALLBACK_THREADS_MAX = "callbackThreadsMax";
    public static final String CALLBACK_ELASTIC_ENABLED = "callbackElasticEnabled";
    public static final String CALLBACK_SCALE_UP_QUEUE_THRESHOLD = "callbackScaleUpQueueThreshold";
    public static final String CALLBACK_SCALE_DOWN_STEPS = "callbackScaleDownSteps";
    public static final String CALLBACK_SCALE_CHECK_INTERVAL_MS = "callbackScaleCheckIntervalMs";
    public static final String CALLBACK_QUEUE_CAPACITY = "callbackQueueCapacity";
    /** When false, each MQTT callback is queued for handling (no last-value-wins coalesce on L0). */
    public static final String INGRESS_COALESCE_ENABLED = "ingressCoalesceEnabled";
    public static final String TELEMETRY_PUBLISH_MODE = "telemetryPublishMode";
    private static final String EVENT_JOURNAL_ONLY = "EVENT_JOURNAL_ONLY";

    private DriverIngress() {
    }

    /**
     * True when push ingress must preserve every sample (FIFO), not last-value-wins coalesce.
     * {@code EVENT_JOURNAL_ONLY} always uses FIFO; otherwise honour {@link #INGRESS_COALESCE_ENABLED}.
     */
    public static boolean resolveFifoIngress(Map<String, String> configuration, boolean defaultCoalesceEnabled) {
        if (configuration != null) {
            String mode = configuration.get(TELEMETRY_PUBLISH_MODE);
            if (mode != null && EVENT_JOURNAL_ONLY.equalsIgnoreCase(mode.trim())) {
                return true;
            }
        }
        return !resolveCoalesceEnabled(configuration, defaultCoalesceEnabled);
    }

    public static int resolveThreads(Map<String, String> configuration, int fallback) {
        return resolvePositive(configuration, CALLBACK_THREADS, fallback);
    }

    public static int resolveCapacity(Map<String, String> configuration, int fallback) {
        return resolvePositive(configuration, CALLBACK_QUEUE_CAPACITY, fallback);
    }

    public static boolean resolveCoalesceEnabled(Map<String, String> configuration, boolean fallback) {
        return resolveBoolean(configuration, INGRESS_COALESCE_ENABLED, fallback);
    }

    public static boolean resolveBoolean(Map<String, String> configuration, String key, boolean fallback) {
        String raw = configuration.get(key);
        return parseBoolean(raw, fallback);
    }

    public static int resolvePositive(Map<String, String> configuration, String key, int fallback) {
        String raw = configuration.get(key);
        return parsePositive(raw, fallback);
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private static int parsePositive(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
