package com.ispf.driver.kafka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KafkaPointTest {

    @Test
    void parsesConsume() {
        KafkaPoint point = KafkaPoint.parse("consume");
        assertEquals(KafkaPoint.KafkaMode.CONSUME, point.mode());
        assertNull(point.payload());
    }

    @Test
    void parsesProduce() {
        KafkaPoint point = KafkaPoint.parse("produce:hello world");
        assertEquals(KafkaPoint.KafkaMode.PRODUCE, point.mode());
        assertEquals("hello world", point.payload());
    }
}
