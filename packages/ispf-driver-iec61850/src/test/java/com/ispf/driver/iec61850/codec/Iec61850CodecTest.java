package com.ispf.driver.iec61850.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Literal TPKT/COTP and BER FloatingPoint vectors for the IEC 61850 MMS client.
 */
class Iec61850CodecTest {

    @Test
    void connectionRequestIsExactTwentyTwoOctets() {
        assertArrayEquals(new byte[] {
                0x03, 0x00, 0x00, 0x16,
                0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x01, 0x00,
                (byte) 0xC0, 0x01, 0x0A,
                (byte) 0xC1, 0x02, 0x00, 0x01,
                (byte) 0xC2, 0x02, 0x00, 0x02
        }, Iec61850Codec.connectionRequest());
        assertEquals(22, Iec61850Codec.connectionRequest().length);
    }

    @Test
    void literalBerFloatInsideCotpDtStartsWithTpkt03_00() throws Exception {
        // Hand-written: TPKT + COTP DT (F0 / TPDU-NR 80) + MMS FloatingPoint 12.5f
        // 12.5f IEEE754 BE = 41 48 00 00; BER 87 05 08 41 48 00 00
        byte[] tpkt = new byte[] {
                0x03, 0x00, 0x00, 0x0E,
                0x02, (byte) 0xF0, (byte) 0x80,
                (byte) 0x87, 0x05, 0x08, 0x41, 0x48, 0x00, 0x00
        };
        assertEquals(0x03, tpkt[0] & 0xFF);
        assertEquals(0x00, tpkt[1] & 0xFF);
        byte[] user = Iec61850Codec.unwrapDataTransfer(tpkt);
        assertEquals(12.5f, Iec61850Codec.decodeFloatingPoint(user), 0.0f);

        byte[] fromStream = Iec61850Codec.readTpkt(new ByteArrayInputStream(tpkt));
        assertArrayEquals(tpkt, fromStream);
    }

    @Test
    void encodeFloatingPointMatchesHandWrittenBer() {
        assertArrayEquals(new byte[] {
                (byte) 0x87, 0x05, 0x08, 0x41, 0x48, 0x00, 0x00
        }, Iec61850Codec.encodeFloatingPoint(12.5f));
    }
}
