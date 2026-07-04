package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverPackPropertiesTest {

    @Test
    void mqttIngressDefaultsAndClamps() {
        DriverPackProperties properties = new DriverPackProperties();
        assertEquals(4, properties.getMqttCallbackThreads());
        assertEquals(10_000, properties.getMqttCallbackQueueCapacity());
        assertTrue(properties.isMqttCallbackElasticEnabled());
        assertEquals(4, properties.resolvedMqttCallbackThreadsMin());
        assertEquals(32, properties.resolvedMqttCallbackThreadsMax());

        properties.setMqttCallbackThreads(0);
        properties.setMqttCallbackQueueCapacity(-5);
        assertEquals(1, properties.getMqttCallbackThreads());
        assertEquals(1, properties.getMqttCallbackQueueCapacity());
    }

    @Test
    void ingressBufferElasticResolvedThreads() {
        DriverPackProperties properties = new DriverPackProperties();
        assertTrue(properties.isIngressBufferElasticEnabled());
        assertEquals(2, properties.resolvedIngressBufferThreadsMin());
        assertEquals(32, properties.resolvedIngressBufferThreadsMax());

        properties.setIngressBufferElasticEnabled(false);
        properties.setIngressBufferThreads(8);
        assertEquals(8, properties.resolvedIngressBufferThreadsMin());
        assertEquals(8, properties.resolvedIngressBufferThreadsMax());
    }
}
