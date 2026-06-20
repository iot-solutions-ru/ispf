package com.ispf.driver.smpp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmppPointTest {

    @Test
    void parsesBind() {
        SmppPoint point = SmppPoint.parse("bind");
        assertEquals(SmppPoint.SmppMode.BIND, point.mode());
    }

    @Test
    void parsesSubmit() {
        SmppPoint point = SmppPoint.parse("+15551234567:Hello");
        assertEquals(SmppPoint.SmppMode.SUBMIT, point.mode());
        assertEquals("+15551234567", point.destination());
        assertEquals("Hello", point.message());
    }
}
