package com.ispf.driver.profibus.codec;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fixed PROFIBUS FDL vectors. Expected octets are written out, not produced by the encoder under test.
 */
class ProfibusFdlCodecTest {

    @Test
    void sd1LiteralMatchesDaSaFcChecksum() {
        byte[] frame = ProfibusFdlCodec.encodeSd1(0x01, 0x02, 0x03);
        assertArrayEquals(new byte[] {
                0x10, 0x01, 0x02, 0x03, 0x06, 0x16
        }, frame);
        ProfibusFdlFrame decoded = assertDecode(frame);
        assertEquals(ProfibusFdlFrame.Kind.SD1, decoded.kind());
        assertEquals(0x01, decoded.da());
        assertEquals(0x02, decoded.sa());
        assertEquals(0x03, decoded.fc());
    }

    @Test
    void sd2RoundTripPreservesPdu() throws Exception {
        byte[] pdu = ProfibusFdlCodec.encodeReadRequest(3, 0);
        byte[] frame = ProfibusFdlCodec.encodeSd2(0x03, 0x02, ProfibusFdlTypes.FC_SRD, pdu);
        ProfibusFdlFrame decoded = ProfibusFdlCodec.decode(frame);
        assertEquals(ProfibusFdlFrame.Kind.SD2, decoded.kind());
        assertEquals(0x03, decoded.da());
        assertEquals(0x02, decoded.sa());
        assertEquals(ProfibusFdlTypes.FC_SRD, decoded.fc());
        assertArrayEquals(pdu, decoded.pdu());
    }

    @Test
    void sd2RejectsBadChecksum() {
        byte[] frame = ProfibusFdlCodec.encodeSd2(1, 2, 3, new byte[] { 0x01 });
        frame[frame.length - 2] = 0x00;
        IOException error = assertThrows(IOException.class, () -> ProfibusFdlCodec.decode(frame));
        assertEquals("PROFIBUS FDL SD2 checksum mismatch", error.getMessage());
    }

    private static ProfibusFdlFrame assertDecode(byte[] frame) {
        try {
            return ProfibusFdlCodec.decode(frame);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
