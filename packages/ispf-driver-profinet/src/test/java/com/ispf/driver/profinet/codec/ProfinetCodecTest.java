package com.ispf.driver.profinet.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed DCP Identify vectors. Expected octets are written out, not produced by copying the encoder.
 */
class ProfinetCodecTest {

    @Test
    void headerOnlyIdentifyMatchesLiteralVector() {
        assertArrayEquals(new byte[] {
                (byte) 0xFE, (byte) 0xFE, 0x05, 0x00,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x01,
                0x00, 0x00
        }, ProfinetCodec.encodeIdentifyRequest());
    }
}
