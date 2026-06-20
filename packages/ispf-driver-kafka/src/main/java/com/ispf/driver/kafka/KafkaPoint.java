package com.ispf.driver.kafka;

import java.util.Locale;

/**
 * Point mapping: {@code consume} or {@code produce:payload}.
 */
public record KafkaPoint(KafkaMode mode, String payload) {

    public enum KafkaMode {
        CONSUME,
        PRODUCE
    }

    public static KafkaPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Kafka point mapping is blank");
        }
        String trimmed = raw.trim();
        if ("consume".equalsIgnoreCase(trimmed)) {
            return new KafkaPoint(KafkaMode.CONSUME, null);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("produce:")) {
            String payload = trimmed.substring("produce:".length());
            if (payload.isBlank()) {
                throw new IllegalArgumentException("Kafka produce payload is blank");
            }
            return new KafkaPoint(KafkaMode.PRODUCE, payload);
        }
        throw new IllegalArgumentException("Unknown Kafka point mapping: " + trimmed);
    }
}
