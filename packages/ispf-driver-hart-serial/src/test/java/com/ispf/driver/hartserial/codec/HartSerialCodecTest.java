package com.ispf.driver.hartserial.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed HART serial short-frame vectors. Expected octets are literals, not derived from the encoder under test.
 */
class HartSerialCodecTest {

    @Test
    void command1WithPreambleMatchesByteStream() {
        byte[] frame = HartSerialCodec.encodeRequest(0, 1);
        assertArrayEquals(new byte[] {
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x02, 0x00, 0x01, 0x00, 0x03
        }, frame);
    }

    @Test
    void shortFrameAloneMatchesCommand1() {
        byte[] pdu = HartSerialCodec.encodeHartCommand(0, 1);
        assertArrayEquals(new byte[] {
                0x02, 0x00, 0x01, 0x00, 0x03
        }, pdu);
    }
}
