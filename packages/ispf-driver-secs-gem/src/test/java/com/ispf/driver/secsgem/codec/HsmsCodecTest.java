package com.ispf.driver.secsgem.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fixed SEMI E37 HSMS vectors. Expected octets are written out, not derived from the encoder.
 */
class HsmsCodecTest {

    @Test
    void selectReqMatchesSemiE37Literal() {
        // Select.req: session 0xFFFF, PType 0, SType 1, system bytes 1, length 10
        byte[] expected = new byte[] {
                0x00, 0x00, 0x00, 0x0A,
                (byte) 0xFF, (byte) 0xFF,
                0x00, 0x00,
                0x00, 0x01,
                0x00, 0x00, 0x00, 0x01
        };
        byte[] frame = HsmsCodec.encodeSelectReq(1);
        assertArrayEquals(expected, frame);
        assertEquals(14, frame.length);
    }
}
