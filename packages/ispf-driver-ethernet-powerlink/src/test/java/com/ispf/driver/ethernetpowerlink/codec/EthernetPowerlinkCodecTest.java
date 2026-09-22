package com.ispf.driver.ethernetpowerlink.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fixed POWERLINK header vectors.
 */
class EthernetPowerlinkCodecTest {

    @Test
    void preqDest1Src240StartsWithLiteralHeader() {
        byte[] frame = EthernetPowerlinkCodec.encodePReq(1, 240, new byte[4]);
        assertEquals(7, frame.length);
        assertArrayEquals(new byte[] { 0x03, 0x01, (byte) 0xF0 },
                new byte[] { frame[0], frame[1], frame[2] });
    }
}
