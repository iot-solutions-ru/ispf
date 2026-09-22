package com.ispf.driver.hartip.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed HART-IP and short-frame vectors. Expected octets are literals, not derived from the encoder under test.
 */
class HartIpCodecTest {

    @Test
    void sessionInitiateMatchesHartIpHeader() {
        byte[] frame = HartIpCodec.encodeSessionInitiate(1);
        assertArrayEquals(new byte[] {
                0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x05,
                0x01, 0x00, 0x00, 0x75, 0x30
        }, frame);
    }

    @Test
    void command1ToPollingAddress0MatchesShortFrame() {
        byte[] pdu = HartIpCodec.encodeHartCommand(0, 1);
        assertArrayEquals(new byte[] {
                0x02, 0x00, 0x01, 0x00, 0x03
        }, pdu);
    }
}
