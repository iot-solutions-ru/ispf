package com.ispf.driver.genicam.codec;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed GigE Vision GVCP discovery vectors. Expected octets are literals, not derived from the encoder under test.
 */
class GenicamCodecTest {

    @Test
    void discoveryCommandHeaderStarts42010002() {
        // Handwritten GVCP discovery header prefix: key 0x42, flag 0x01, command 0x0002
        byte[] expectedHeader = new byte[] { 0x42, 0x01, 0x00, 0x02 };
        byte[] frame = GenicamCodec.encodeDiscoveryCommand(1);
        assertArrayEquals(expectedHeader, Arrays.copyOf(frame, 4));
        assertArrayEquals(new byte[] {
                0x42, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01
        }, frame);
    }
}
