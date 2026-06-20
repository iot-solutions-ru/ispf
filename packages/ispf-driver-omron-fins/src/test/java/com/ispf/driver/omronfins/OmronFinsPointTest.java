package com.ispf.driver.omronfins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OmronFinsPointTest {

    @Test
    void parsesMemoryAreaRead() {
        OmronFinsPoint point = OmronFinsPoint.parse("DM:100:1");
        assertEquals("DM", point.memoryArea());
        assertEquals(100, point.address());
        assertEquals(1, point.count());
        assertEquals((byte) 0x82, point.memoryAreaCode());
    }
}
