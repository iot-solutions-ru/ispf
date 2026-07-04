package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverPackPropertiesTest {

    @Test
    void mqttIngressDefaultsAndClamps() {
        DriverPackProperties properties = new DriverPackProperties();
        assertEquals(4, properties.getMqttCallbackThreads());
        assertEquals(10_000, properties.getMqttCallbackQueueCapacity());

        properties.setMqttCallbackThreads(0);
        properties.setMqttCallbackQueueCapacity(-5);
        assertEquals(1, properties.getMqttCallbackThreads());
        assertEquals(1, properties.getMqttCallbackQueueCapacity());
    }
}
