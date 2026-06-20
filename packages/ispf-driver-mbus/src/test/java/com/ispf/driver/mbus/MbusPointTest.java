package com.ispf.driver.mbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MbusPointTest {

    @Test
    void parsesRegisterMapping() {
        MbusPoint point = MbusPoint.parse("1:12345678:energy");
        assertEquals(1, point.primaryAddress());
        assertEquals(12345678, point.secondaryAddress());
        assertEquals("energy", point.register());
    }
}
