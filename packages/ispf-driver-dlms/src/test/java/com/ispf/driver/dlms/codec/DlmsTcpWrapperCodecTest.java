package com.ispf.driver.dlms.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DlmsTcpWrapperCodecTest {

    @Test
    void getRequestNormalLockedFrame() throws Exception {
        // Handwritten IEC 62056-47 TCP WRAPPER + Get-Request-Normal octets (not taken from the encoder).
        byte[] expected = new byte[] {
                0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x0D,
                (byte) 0xC0, 0x01, 0x01, 0x00, 0x01, 0x00, 0x00, 0x60, 0x01, 0x00, (byte) 0xFF, 0x02, 0x00
        };
        byte[] apdu = DlmsTcpWrapperCodec.getRequest(DlmsObjectType.DATA, "0.0.96.1.0.255", 2);
        byte[] frame = DlmsTcpWrapperCodec.wrapperFrame(1, 1, apdu);
        assertArrayEquals(expected, frame);
    }
}
