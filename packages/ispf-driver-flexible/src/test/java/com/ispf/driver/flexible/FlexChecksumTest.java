package com.ispf.driver.flexible;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlexChecksumTest {

    @Test
    void verifiesValidFrame() throws Exception {
        String body = "\u0001i20101250629150001R0000073F800000";
        byte[] beforeMarker = body.getBytes(StandardCharsets.US_ASCII);
        String checksum = computeChecksum(beforeMarker);
        byte[] frame = (body + "&&" + checksum + "\u0003").getBytes(StandardCharsets.US_ASCII);

        String payload = FlexChecksum.verifyAndPayload(frame, "sum16-complement-hex", "&&", 4);
        assertEquals(body, payload);
    }

    @Test
    void rejectsInvalidChecksum() {
        byte[] frame = "\u0001TEST&&FFFF\u0003".getBytes(StandardCharsets.US_ASCII);
        assertThrows(com.ispf.driver.DriverException.class,
                () -> FlexChecksum.verifyAndPayload(frame, "sum16-complement-hex", "&&", 4));
    }

    static String computeChecksum(byte[] messageBeforeMarker) {
        int sum = 0;
        for (byte b : messageBeforeMarker) {
            sum = (sum + (b & 0xFF)) & 0xFFFF;
        }
        int checksum = (0x10000 - sum) & 0xFFFF;
        return String.format("%04X", checksum);
    }
}
