package com.ispf.driver.mitsubishimelsec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Clean-room MC Protocol / SLMP 3E binary frame helpers for MELSEC Ethernet (TCP).
 * <p>
 * Implements the publicly documented 3E binary request/response envelope used by Q/L/iQ-R
 * Ethernet modules for device memory access. Subset only: batch read/write of D registers.
 */
final class MitsubishiMelsecFrame {

    static final int SUBHEADER_REQUEST = 0x0050;
    static final int SUBHEADER_RESPONSE = 0x00D0;
    static final int CMD_BATCH_READ = 0x0401;
    static final int CMD_BATCH_WRITE = 0x1401;
    static final int SUBCOMMAND_WORD = 0x0000;

    private MitsubishiMelsecFrame() {
    }

    static byte[] buildRequest(
            int networkNo,
            int pcNo,
            int ioNo,
            int stationNo,
            int monitoringTimer,
            int command,
            MitsubishiMelsecPoint point,
            int[] writeWords
    ) {
        int wordBytes = writeWords == null ? 0 : writeWords.length * 2;
        ByteBuffer body = ByteBuffer.allocate(12 + wordBytes).order(ByteOrder.LITTLE_ENDIAN);
        body.putShort((short) (monitoringTimer & 0xFFFF));
        body.putShort((short) (command & 0xFFFF));
        body.putShort((short) SUBCOMMAND_WORD);
        int addr = point.address();
        body.put((byte) (addr & 0xFF));
        body.put((byte) ((addr >> 8) & 0xFF));
        body.put((byte) ((addr >> 16) & 0xFF));
        body.put(point.deviceCode());
        body.putShort((short) (point.wordCount() & 0xFFFF));
        if (writeWords != null) {
            for (int word : writeWords) {
                body.putShort((short) (word & 0xFFFF));
            }
        }
        byte[] bodyBytes = body.array();

        ByteBuffer frame = ByteBuffer.allocate(11 + bodyBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) SUBHEADER_REQUEST);
        frame.put((byte) (networkNo & 0xFF));
        frame.put((byte) (pcNo & 0xFF));
        frame.putShort((short) (ioNo & 0xFFFF));
        frame.put((byte) (stationNo & 0xFF));
        frame.putShort((short) bodyBytes.length);
        frame.put(bodyBytes);
        return frame.array();
    }

    static ParsedResponse parseResponse(byte[] header9, byte[] payload) {
        if (header9.length < 9) {
            throw new IllegalArgumentException("MELSEC response header incomplete");
        }
        int subheader = (header9[0] & 0xFF) | ((header9[1] & 0xFF) << 8);
        if (subheader != SUBHEADER_RESPONSE) {
            throw new IllegalArgumentException("Unexpected MELSEC subheader 0x" + Integer.toHexString(subheader));
        }
        if (payload.length < 2) {
            throw new IllegalArgumentException("MELSEC response payload too short");
        }
        int endCode = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8);
        return new ParsedResponse(endCode, payload);
    }

    static int[] extractWords(byte[] payload, int count) {
        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            int offset = 2 + i * 2;
            if (offset + 1 >= payload.length) {
                words[i] = 0;
            } else {
                words[i] = (payload[offset] & 0xFF) | ((payload[offset + 1] & 0xFF) << 8);
            }
        }
        return words;
    }

    record ParsedResponse(int endCode, byte[] payload) {
    }
}
