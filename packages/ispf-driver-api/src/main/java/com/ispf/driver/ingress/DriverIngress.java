package com.ispf.driver.ingress;

import java.util.Map;

/** Shared ingress tuning keys for push drivers and server-side driver bridges. */
public final class DriverIngress {

    public static final String CALLBACK_THREADS = "callbackThreads";
    public static final String CALLBACK_QUEUE_CAPACITY = "callbackQueueCapacity";

    private DriverIngress() {
    }

    public static int resolveThreads(Map<String, String> configuration, int fallback) {
        return parsePositive(configuration.get(CALLBACK_THREADS), fallback);
    }

    public static int resolveCapacity(Map<String, String> configuration, int fallback) {
        return parsePositive(configuration.get(CALLBACK_QUEUE_CAPACITY), fallback);
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
