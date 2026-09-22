package com.ispf.driver.interbus.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed INTERBUS process-image vectors.
 */
class InterbusCodecTest {

    @Test
    void processImage1234MatchesLiteralVector() {
        assertArrayEquals(new byte[] { 0x00, 0x02, 0x12, 0x34 },
                InterbusCodec.encodeProcessImage(0x1234));
    }
}
