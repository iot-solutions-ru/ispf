package com.ispf.driver.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fast-path extraction of device timestamps from JSON MQTT payloads. */
final class MqttPayloadTimestamps {

    private static final Pattern ISO_FIELD = Pattern.compile(
            "\"(?:observedAt|timestamp|ts|time)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EPOCH_FIELD = Pattern.compile(
            "\"(?:observedAt|timestamp|ts|time)\"\\s*:\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private MqttPayloadTimestamps() {
    }

    static Instant resolve(byte[] payloadBytes) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            return Instant.now();
        }
        if (payloadBytes[0] != '{') {
            return parseScalarEpoch(payloadBytes);
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        if (payload.isBlank()) {
            return Instant.now();
        }
        Matcher isoMatch = ISO_FIELD.matcher(payload);
        if (isoMatch.find()) {
            try {
                return Instant.parse(isoMatch.group(1));
            } catch (Exception ignored) {
                // fall through
            }
        }
        Matcher epochMatch = EPOCH_FIELD.matcher(payload);
        if (epochMatch.find()) {
            long raw = Long.parseLong(epochMatch.group(1));
            return raw > 1_000_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }
        return Instant.now();
    }

    private static Instant parseScalarEpoch(byte[] payloadBytes) {
        try {
            String text = new String(payloadBytes, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return Instant.now();
            }
            long raw = Long.parseLong(text);
            if (raw > 1_000_000_000_000L) {
                return Instant.ofEpochMilli(raw);
            }
            if (raw > 1_000_000_000L) {
                return Instant.ofEpochSecond(raw);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return Instant.now();
    }
}
