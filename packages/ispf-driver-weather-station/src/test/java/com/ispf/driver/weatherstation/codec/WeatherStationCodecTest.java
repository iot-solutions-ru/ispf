package com.ispf.driver.weatherstation.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed Davis Vantage LOOP vectors. Expected octets are literals, not derived from the encoder under test.
 */
class WeatherStationCodecTest {

    @Test
    void loopCommandIsExactly4c4f4f500a() {
        // Handwritten LOOP\n (not produced by the encoder)
        byte[] expected = new byte[] { 0x4C, 0x4F, 0x4F, 0x50, 0x0A };
        assertArrayEquals(expected, WeatherStationCodec.encodeLoopCommand());
    }
}
