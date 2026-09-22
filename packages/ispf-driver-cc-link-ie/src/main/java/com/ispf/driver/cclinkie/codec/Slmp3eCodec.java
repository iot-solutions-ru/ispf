package com.ispf.driver.cclinkie.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * MELSEC SLMP / MC Protocol 3E binary request and response helpers.
 * <p>
 * Shared layout used by CC-Link IE gateways that speak SLMP for D/R/W device access
 * over TCP — not the CC-Link RS-485 ASIC.
 */
public final class Slmp3eCodec {

    public static final int SUBHEADER_REQUEST = 0x0050;
    public static final int SUBHEADER_RESPONSE = 0x00D0;
    public static final int CMD_BATCH_READ = 0x0401;
    public static final int CMD_BATCH_WRITE = 0x1401;
    public static final int SUBCOMMAND_WORD = 0x0000;

    public static final int DEVICE_D = 0xA8;
    public static final int DEVICE_R = 0x9C;
    public static final int DEVICE_W = 0xB4;

    private Slmp3eCodec() {
    }

    public static int deviceCodeByte(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("SLMP device code is blank");
        }
        return switch (deviceCode.trim().toUpperCase(Locale.ROOT)) {
            case "D" -> DEVICE_D;
            case "R" -> DEVICE_R;
            case "W" -> DEVICE_W;
            default -> throw new IllegalArgumentException("Unsupported SLMP device code: " + deviceCode);
        };
    }

    /**
     * Build a 3E binary batch read/write request.
     * Device code precedes the 3-byte head address (gateway / IE layout).
     */
    public static byte[] buildRequest(
            int networkNo,
            int pcNo,
            int ioNo,
            int stationNo,
            int monitoringTimer,
            int command,
            int deviceCodeByte,
            int address,
            int wordCount,
            int[] writeWords
    ) {
        int wordBytes = writeWords == null ? 0 : writeWords.length * 2;
        ByteBuffer body = ByteBuffer.allocate(12 + wordBytes).order(ByteOrder.LITTLE_ENDIAN);
        body.putShort((short) (monitoringTimer & 0xFFFF));
        body.putShort((short) (command & 0xFFFF));
        body.putShort((short) SUBCOMMAND_WORD);
        body.put((byte) (deviceCodeByte & 0xFF));
        body.put((byte) (address & 0xFF));
        body.put((byte) ((address >> 8) & 0xFF));
        body.put((byte) ((address >> 16) & 0xFF));
        body.putShort((short) (wordCount & 0xFFFF));
        if (writeWords != null) {
            for (int word : writeWords) {
                body.putShort((short) (word & 0xFFFF));
            }
        }
        byte[] bodyBytes = body.array();

        ByteBuffer frame = ByteBuffer.allocate(9 + bodyBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) SUBHEADER_REQUEST);
        frame.put((byte) (networkNo & 0xFF));
        frame.put((byte) (pcNo & 0xFF));
        frame.putShort((short) (ioNo & 0xFFFF));
        frame.put((byte) (stationNo & 0xFF));
        frame.putShort((short) bodyBytes.length);
        frame.put(bodyBytes);
        return frame.array();
    }

    /** Read D100, 1 word, timer 0x0010, network 0, PC 0xFF, IO 0x03FF, station 0. */
    public static byte[] buildReadD100Reference() {
        return buildRequest(0, 0xFF, 0x03FF, 0, 0x0010, CMD_BATCH_READ, DEVICE_D, 100, 1, null);
    }

    public static ParsedResponse parseResponse(byte[] header9, byte[] payload) {
        if (header9 == null || header9.length < 9) {
            throw new IllegalArgumentException("SLMP response header incomplete");
        }
        int subheader = (header9[0] & 0xFF) | ((header9[1] & 0xFF) << 8);
        if (subheader != SUBHEADER_RESPONSE) {
            throw new IllegalArgumentException(
                    "Unexpected SLMP subheader 0x" + Integer.toHexString(subheader));
        }
        if (payload == null || payload.length < 2) {
            throw new IllegalArgumentException("SLMP response payload too short");
        }
        int endCode = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8);
        return new ParsedResponse(endCode);
    }

    public static int[] extractWords(byte[] payload, int count) {
        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            int offset = 2 + i * 2;
            if (payload == null || offset + 1 >= payload.length) {
                words[i] = 0;
            } else {
                words[i] = (payload[offset] & 0xFF) | ((payload[offset + 1] & 0xFF) << 8);
            }
        }
        return words;
    }

    public static int responseDataLength(byte[] header9) {
        return (header9[7] & 0xFF) | ((header9[8] & 0xFF) << 8);
    }

    /** Response end-code holder (no byte-array components). */
    public record ParsedResponse(int endCode) {
    }
}
