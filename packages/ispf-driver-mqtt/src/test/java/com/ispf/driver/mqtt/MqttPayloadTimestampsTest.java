package com.ispf.driver.mqtt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttPayloadTimestampsTest {

    @Test
    void parsesIsoFieldFromJson() {
        byte[] payload = "{\"value\":12.3,\"timestamp\":\"2024-06-01T10:15:30Z\"}"
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(Instant.parse("2024-06-01T10:15:30Z"), MqttPayloadTimestamps.resolve(payload));
    }

    @Test
    void parsesEpochMillisFromJson() {
        byte[] payload = "{\"ts\":1717239330000}".getBytes(StandardCharsets.UTF_8);
        assertEquals(Instant.ofEpochMilli(1717239330000L), MqttPayloadTimestamps.resolve(payload));
    }

    @Test
    void parsesScalarEpochMillisPayload() {
        byte[] payload = "1717239330000".getBytes(StandardCharsets.UTF_8);
        assertEquals(Instant.ofEpochMilli(1717239330000L), MqttPayloadTimestamps.resolve(payload));
    }

    @Test
    void skipsRegexForScalarPayload() {
        byte[] payload = "42.5".getBytes(StandardCharsets.UTF_8);
        Instant before = Instant.now();
        Instant observed = MqttPayloadTimestamps.resolve(payload);
        Instant after = Instant.now();
        assertTrue(!observed.isBefore(before) && !observed.isAfter(after));
    }
}
