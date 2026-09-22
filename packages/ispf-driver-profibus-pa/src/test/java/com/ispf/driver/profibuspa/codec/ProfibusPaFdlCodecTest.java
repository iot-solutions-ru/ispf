package com.ispf.driver.profibuspa.codec;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fixed PROFIBUS FDL vectors. Expected octets are written out, not produced by the encoder under test.
 */
class ProfibusPaFdlCodecTest {

    @Test
    void sd1LiteralMatchesDaSaFcChecksum() {
        byte[] frame = ProfibusPaFdlCodec.encodeSd1(0x01, 0x02, 0x03);
        assertArrayEquals(new byte[] {
                0x10, 0x01, 0x02, 0x03, 0x06, 0x16
        }, frame);
        try {
            ProfibusPaFdlFrame decoded = ProfibusPaFdlCodec.decode(frame);
            assertEquals(ProfibusPaFdlFrame.Kind.SD1, decoded.kind());
            assertEquals(0x01, decoded.da());
            assertEquals(0x02, decoded.sa());
            assertEquals(0x03, decoded.fc());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void sd2RoundTripPreservesPdu() throws Exception {
        byte[] pdu = ProfibusPaFdlCodec.encodeReadRequest("slot-pv", 1);
        byte[] frame = ProfibusPaFdlCodec.encodeSd2(0x01, 0x02, ProfibusPaFdlTypes.FC_SRD, pdu);
        ProfibusPaFdlFrame decoded = ProfibusPaFdlCodec.decode(frame);
        assertEquals(ProfibusPaFdlFrame.Kind.SD2, decoded.kind());
        assertArrayEquals(pdu, decoded.pdu());
    }

    @Test
    void sd2RejectsBadChecksum() {
        byte[] frame = ProfibusPaFdlCodec.encodeSd2(1, 2, 3, new byte[] { 0x01 });
        frame[frame.length - 2] = 0x00;
        IOException error = assertThrows(IOException.class, () -> ProfibusPaFdlCodec.decode(frame));
        assertEquals("PROFIBUS FDL SD2 checksum mismatch", error.getMessage());
    }
}
