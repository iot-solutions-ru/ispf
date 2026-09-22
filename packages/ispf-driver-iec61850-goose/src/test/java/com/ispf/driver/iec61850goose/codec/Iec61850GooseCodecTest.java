package com.ispf.driver.iec61850goose.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Literal IEC 61850-8-1 GOOSE header vectors.
 */
class Iec61850GooseCodecTest {

    @Test
    void gooseHeaderWithFourByteApduIsExact() {
        byte[] frame = Iec61850GooseCodec.encode(0x3000, new byte[] { 0x01, 0x02, 0x03, 0x04 });
        assertArrayEquals(new byte[] {
                0x30, 0x00, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04
        }, frame);
        assertEquals(12, frame.length);
    }
}
