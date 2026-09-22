package com.ispf.driver.iec61850sv.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Literal IEC 61850-9-2 SAV header vectors.
 */
class Iec61850SvCodecTest {

    @Test
    void savHeaderWithFourByteAsduIsExact() {
        byte[] frame = Iec61850SvCodec.encode(0x4000, new byte[] { 0x01, 0x02, 0x03, 0x04 });
        assertArrayEquals(new byte[] {
                0x40, 0x00, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04
        }, frame);
        assertEquals(12, frame.length);
    }
}
