package com.ispf.driver.dnp3.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Dnp3TcpCodecTest {

    @Test
    void resetLinkFrameMatchesHandwrittenOctets() {
        byte[] expected = new byte[] {
                0x05, 0x64, 0x05, (byte) 0xC0, 0x01, 0x00, 0x00, 0x00, (byte) 0x91, (byte) 0xF8
        };
        assertArrayEquals(expected, Dnp3TcpCodec.resetLinkRequest(1, 0));
    }

    @Test
    void crc16DnpCatalogueAndHeader() {
        assertEquals(0xEA82, independentBitLoopCrc("123456789".getBytes(StandardCharsets.US_ASCII)));
        byte[] header = new byte[] {
                0x05, 0x64, 0x05, (byte) 0xC0, 0x01, 0x00, 0x00, 0x00
        };
        assertEquals(0xF891, Dnp3TcpCodec.crc16(header));
    }

    /** Independent CRC-16/DNP bit loop (reflected poly 0xA6BC, init 0, xorout 0xFFFF). */
    private static int independentBitLoopCrc(byte[] data) {
        int crc = 0;
        for (byte datum : data) {
            crc ^= Byte.toUnsignedInt(datum);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA6BC;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return (crc ^ 0xFFFF) & 0xFFFF;
    }
}
