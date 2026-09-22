package com.ispf.driver.iec103.codec;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fixed FT1.2 vectors. The expected octets are written out, not produced by the encoder under test.
 */
class Iec103CodecTest {

    @Test
    void resetLinkFrameMatchesFt12Layout() {
        int control = Iec103Codec.primaryControl(Iec103Types.FC_RESET_REMOTE_LINK, false, false);
        byte[] frame = Iec103Codec.encodeFixed(control, 1);
        assertArrayEquals(new byte[] {
                0x10, 0x40, 0x01, 0x41, 0x16
        }, frame);
    }

    @Test
    void firstUserDataControlIs0x73() {
        int control = Iec103Codec.primaryControl(Iec103Types.FC_USER_DATA, true, true);
        assertEquals(0x73, control);
    }

    @Test
    void variableFrameRejectsBadChecksum() {
        byte[] asdu = Iec103Codec.encodeInterrogation(1);
        byte[] frame = Iec103Codec.encodeVariable(0x73, 1, asdu);
        frame[frame.length - 2] = 0x00;
        IOException error = assertThrows(IOException.class, () -> Iec103Codec.decode(frame));
        assertEquals("IEC 103 variable frame checksum mismatch", error.getMessage());
    }
}
