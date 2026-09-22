package com.ispf.driver.foundationfieldbus.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed FDA/HSE probe vectors.
 */
class FoundationFieldbusCodecTest {

    @Test
    void fdaProbeLengthZeroMatchesLiteralVector() {
        assertArrayEquals(new byte[] { 0x01, 0x00, 0x00, 0x00 },
                FoundationFieldbusCodec.encodeFdaProbe(0));
    }
}
