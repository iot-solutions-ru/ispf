package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatsPropertiesTest {

    @Test
    void appliesJetStreamDefaults() {
        NatsProperties properties = new NatsProperties(
                true,
                "nats://localhost:4222",
                true,
                "replica-1",
                true,
                null,
                0,
                null,
                65536,
                true,
                2,
                8,
                50,
                6,
                30
        );

        assertTrue(properties.jetStreamEnabled());
        assertEquals("ispf-automation", properties.jetStreamStreamName());
        assertEquals(24, properties.jetStreamMaxAgeHours());
        assertEquals("ispf-replica-", properties.jetStreamReplicaConsumerPrefix());
    }

    @Test
    void jetStreamDisabledByDefault() {
        NatsProperties properties = new NatsProperties(
                false,
                null,
                true,
                null,
                false,
                "custom-stream",
                48,
                "node-",
                65536,
                true,
                2,
                8,
                50,
                6,
                30
        );

        assertFalse(properties.enabled());
        assertFalse(properties.jetStreamEnabled());
        assertEquals("custom-stream", properties.jetStreamStreamName());
        assertEquals(48, properties.jetStreamMaxAgeHours());
        assertEquals("node-", properties.jetStreamReplicaConsumerPrefix());
    }
}
