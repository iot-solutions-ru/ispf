package com.ispf.driver.wmbus.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Minimal Wireless M-Bus / OMS short-frame lab parser for a TCP gateway.
 * <p>
 * Not an RF PHY. Clean-room Apache-2.0, JDK only.
 * <p>
 * Lab short frame layout (no CRC): {@code L | C | M(2 LE) | ID(4 LE) | Ver | Type | CI | float32 BE value}.
 * {@code L} is the count of bytes following L (C through value inclusive).
 */
public final class WmbusLabCodec {

    public static final int CI_LAB_FULL_DATA = 0x78;

    private WmbusLabCodec() {
    }

    public static byte[] encodeShortFrame(int manufacturer, long deviceId, int version, int deviceType, float value) {
        byte[] idBytes = new byte[4];
        ByteBuffer.wrap(idBytes).order(ByteOrder.LITTLE_ENDIAN).putInt((int) (deviceId & 0xFFFFFFFFL));
        ByteBuffer body = ByteBuffer.allocate(1 + 2 + 4 + 1 + 1 + 1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        body.put((byte) 0x44); // C-field SND_NR lab
        body.putShort((short) (manufacturer & 0xFFFF));
        body.put(idBytes);
        body.put((byte) (version & 0xFF));
        body.put((byte) (deviceType & 0xFF));
        body.put((byte) CI_LAB_FULL_DATA);
        body.order(ByteOrder.BIG_ENDIAN);
        body.putInt(Float.floatToIntBits(value));
        byte[] payload = body.array();
        byte[] frame = new byte[1 + payload.length];
        frame[0] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, frame, 1, payload.length);
        return frame;
    }

    public static ParsedTelegram parse(byte[] frame) {
        if (frame == null || frame.length < 12) {
            throw new IllegalArgumentException("wM-Bus telegram too short");
        }
        int lField = frame[0] & 0xFF;
        if (frame.length < 1 + lField) {
            throw new IllegalArgumentException("Incomplete wM-Bus telegram");
        }
        int manufacturer = (frame[2] & 0xFF) | ((frame[3] & 0xFF) << 8);
        long deviceId = Integer.toUnsignedLong(
                (frame[4] & 0xFF)
                        | ((frame[5] & 0xFF) << 8)
                        | ((frame[6] & 0xFF) << 16)
                        | ((frame[7] & 0xFF) << 24));
        int version = frame[8] & 0xFF;
        int deviceType = frame[9] & 0xFF;
        int ci = frame[10] & 0xFF;
        if (ci != CI_LAB_FULL_DATA) {
            throw new IllegalArgumentException("Unsupported CI field for lab: 0x" + Integer.toHexString(ci));
        }
        if (1 + lField < 15) {
            throw new IllegalArgumentException("CI lab payload missing float value");
        }
        float value = Float.intBitsToFloat(
                ((frame[11] & 0xFF) << 24)
                        | ((frame[12] & 0xFF) << 16)
                        | ((frame[13] & 0xFF) << 8)
                        | (frame[14] & 0xFF));
        return new ParsedTelegram(manufacturer, deviceId, version, deviceType, ci, value);
    }

    public static byte[] decodeHexTelegram(String hex) {
        String cleaned = hex.trim().replace(" ", "").toUpperCase(Locale.ROOT);
        if ((cleaned.length() % 2) != 0) {
            throw new IllegalArgumentException("Odd hex telegram length");
        }
        byte[] out = new byte[cleaned.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String toHex(byte[] frame) {
        StringBuilder sb = new StringBuilder(frame.length * 2);
        for (byte value : frame) {
            sb.append(String.format(Locale.ROOT, "%02X", value & 0xFF));
        }
        return sb.toString();
    }

    public static String deviceIdHex(long deviceId) {
        return String.format(Locale.ROOT, "%08X", deviceId & 0xFFFFFFFFL);
    }

    public record ParsedTelegram(
            int manufacturer,
            long deviceId,
            int version,
            int deviceType,
            int ci,
            float value
    ) {
    }
}
