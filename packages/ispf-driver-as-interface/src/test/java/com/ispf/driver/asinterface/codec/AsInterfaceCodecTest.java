package com.ispf.driver.asinterface.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Fixed AS-Interface master-call vectors.
 */
class AsInterfaceCodecTest {

    @Test
    void address0Data0MatchesLiteralVector() {
        assertArrayEquals(new byte[] { 0x00, 0x00 }, AsInterfaceCodec.encodeMasterCall(0, 0));
    }
}
