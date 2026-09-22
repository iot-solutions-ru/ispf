package com.ispf.driver.devicenet.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fixed CIP Get_Attribute_Single vectors for DeviceNet.
 */
class DeviceNetCodecTest {

    @Test
    void getAttributeSingleClass1Inst1Attr1MatchesLiteralVector() {
        byte[] expected = new byte[] {
                0x0E, 0x03, 0x20, 0x01, 0x24, 0x01, 0x30, 0x01
        };
        byte[] actual = DeviceNetCodec.encodeGetAttributeSingle(1, 1, 1);
        assertEquals(0x0E, actual[0] & 0xFF);
        assertArrayEquals(expected, actual);
    }
}
