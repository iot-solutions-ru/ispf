package com.ispf.driver.iec101.codec;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fixed FT1.2 vectors. The expected octets are written out, not produced by the encoder under test.
 */
class Iec101CodecTest {

    @Test
    void resetLinkFrameMatchesFt12Layout() {
        int control = Iec101Codec.primaryControl(Iec101Types.FC_RESET_REMOTE_LINK, false, false);
        byte[] frame = Iec101Codec.encodeFixed(control, 1);
        assertArrayEquals(new byte[] {
                0x10, 0x40, 0x01, 0x41, 0x16
        }, frame);
    }

    @Test
    void stationInterrogationFrameMatchesFt12Layout() {
        int control = Iec101Codec.primaryControl(Iec101Types.FC_USER_DATA, true, true);
        byte[] asdu = Iec101Codec.encodeInterrogation(1);
        byte[] frame = Iec101Codec.encodeVariable(control, 1, asdu);
        assertArrayEquals(new byte[] {
                0x68, 0x0A, 0x0A, 0x68,
                0x73, 0x01,
                0x64, 0x01, 0x06, 0x01, 0x00, 0x00, 0x00, 0x14,
                (byte) 0xF4, 0x16
        }, frame);
    }

    @Test
    void variableFrameRejectsBadChecksum() {
        byte[] frame = new byte[] {
                0x68, 0x0A, 0x0A, 0x68,
                0x73, 0x01,
                0x64, 0x01, 0x06, 0x01, 0x00, 0x00, 0x00, 0x14,
                0x00, 0x16
        };
        IOException error = assertThrows(IOException.class, () -> Iec101Codec.decode(frame));
        assertEquals("IEC 101 variable frame checksum mismatch", error.getMessage());
    }
}
