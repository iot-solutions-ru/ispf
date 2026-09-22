package com.ispf.driver.iolink.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed IO-Link ISDU read vectors.
 */
class IoLinkCodecTest {

    @Test
    void isduReadPort1Index0010Sub0MatchesLiteralVector() {
        assertArrayEquals(new byte[] { 0x01, 0x00, 0x10, 0x00 },
                IoLinkCodec.encodeIsduRead(1, 0x0010, 0));
    }
}
