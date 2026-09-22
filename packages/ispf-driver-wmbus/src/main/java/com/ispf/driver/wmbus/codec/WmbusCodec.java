package com.ispf.driver.wmbus.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Wireless M-Bus / OMS Format-A link frames for a TCP serial-server transport.
 * <p>
 * Not an RF PHY. Clean-room Apache-2.0, JDK only. CRC-16/EN-13757 over each
 * block (poly {@code 0x3D65}, init {@code 0}, no reflection, xorout {@code 0xFFFF}),
 * CRC appended little-endian after the first 10-byte header block and after
 * subsequent data blocks as in EN 13757-4 Format A. Full application frames
 * use CI {@code 0x7A}.
 */
public final class WmbusCodec {

    public static final int CI_FULL_APPLICATION = 0x7A;
    public static final int C_SND_NR = 0x44;
    public static final int C_REQ_UD2 = 0x5B;

    private static final int HEADER_BLOCK_LEN = 10;
    private static final int DATA_BLOCK_LEN = 16;
    private static final int CRC_POLY = 0x3D65;

    private WmbusCodec() {
    }

    /**
     * CRC-16/EN-13757: poly {@code 0x3D65}, init {@code 0}, no reflection, xorout {@code 0xFFFF}.
     * Check value for {@code 123456789} is {@code 0xC2B7}.
     */
    public static int crc16(byte[] data, int offset, int length) {
        int crc = 0x0000;
        for (int i = 0; i < length; i++) {
            crc ^= (data[offset + i] & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ CRC_POLY) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return (crc ^ 0xFFFF) & 0xFFFF;
    }

    public static int crc16(byte[] data) {
        return crc16(data, 0, data.length);
    }

    public static byte[] encodeTelegram(int manufacturer, long deviceId, int version, int deviceType, float value) {
        byte[] app = new byte[5];
        app[0] = (byte) CI_FULL_APPLICATION;
        ByteBuffer.wrap(app, 1, 4).order(ByteOrder.BIG_ENDIAN).putInt(Float.floatToIntBits(value));
        return encodeFormatA(C_SND_NR, manufacturer, deviceId, version, deviceType, app);
    }

    public static byte[] encodePollRequest(int manufacturer, long deviceId, int version, int deviceType) {
        return encodeFormatA(C_REQ_UD2, manufacturer, deviceId, version, deviceType, new byte[] {
                (byte) CI_FULL_APPLICATION
        });
    }

    public static byte[] encodeFormatA(
            int control,
            int manufacturer,
            long deviceId,
            int version,
            int deviceType,
            byte[] application
    ) {
        byte[] app = application == null ? new byte[0] : application;
        int lField = 9 + app.length;
        if (lField > 255) {
            throw new IllegalArgumentException("wM-Bus L-field exceeds 255");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(lField + 1 + 2 * ((app.length + DATA_BLOCK_LEN - 1) / DATA_BLOCK_LEN + 1));
        byte[] header = new byte[HEADER_BLOCK_LEN];
        header[0] = (byte) (lField & 0xFF);
        header[1] = (byte) (control & 0xFF);
        header[2] = (byte) (manufacturer & 0xFF);
        header[3] = (byte) ((manufacturer >>> 8) & 0xFF);
        int id = (int) (deviceId & 0xFFFFFFFFL);
        header[4] = (byte) (id & 0xFF);
        header[5] = (byte) ((id >>> 8) & 0xFF);
        header[6] = (byte) ((id >>> 16) & 0xFF);
        header[7] = (byte) ((id >>> 24) & 0xFF);
        header[8] = (byte) (version & 0xFF);
        header[9] = (byte) (deviceType & 0xFF);
        out.writeBytes(header);
        writeCrcLe(out, crc16(header));
        int appOffset = 0;
        while (appOffset < app.length) {
            int blockLen = Math.min(DATA_BLOCK_LEN, app.length - appOffset);
            out.write(app, appOffset, blockLen);
            writeCrcLe(out, crc16(app, appOffset, blockLen));
            appOffset += blockLen;
        }
        return out.toByteArray();
    }

    public static ParsedTelegram parse(byte[] frame) {
        if (frame == null || frame.length < HEADER_BLOCK_LEN + 2) {
            throw new IllegalArgumentException("wM-Bus telegram too short");
        }
        int lField = frame[0] & 0xFF;
        byte[] header = new byte[HEADER_BLOCK_LEN];
        System.arraycopy(frame, 0, header, 0, HEADER_BLOCK_LEN);
        int headerCrc = readCrcLe(frame, HEADER_BLOCK_LEN);
        if (headerCrc != crc16(header)) {
            throw new IllegalArgumentException("wM-Bus header CRC mismatch");
        }
        int appLen = lField - 9;
        if (appLen < 1) {
            throw new IllegalArgumentException("wM-Bus application payload missing");
        }
        byte[] application = new byte[appLen];
        int framePos = HEADER_BLOCK_LEN + 2;
        int appOffset = 0;
        while (appOffset < appLen) {
            int blockLen = Math.min(DATA_BLOCK_LEN, appLen - appOffset);
            if (framePos + blockLen + 2 > frame.length) {
                throw new IllegalArgumentException("Incomplete wM-Bus telegram");
            }
            System.arraycopy(frame, framePos, application, appOffset, blockLen);
            int blockCrc = readCrcLe(frame, framePos + blockLen);
            if (blockCrc != crc16(application, appOffset, blockLen)) {
                throw new IllegalArgumentException("wM-Bus data CRC mismatch");
            }
            framePos += blockLen + 2;
            appOffset += blockLen;
        }
        int manufacturer = (header[2] & 0xFF) | ((header[3] & 0xFF) << 8);
        long deviceId = Integer.toUnsignedLong(
                (header[4] & 0xFF)
                        | ((header[5] & 0xFF) << 8)
                        | ((header[6] & 0xFF) << 16)
                        | ((header[7] & 0xFF) << 24));
        int version = header[8] & 0xFF;
        int deviceType = header[9] & 0xFF;
        int ci = application[0] & 0xFF;
        if (ci != CI_FULL_APPLICATION) {
            throw new IllegalArgumentException("Unsupported CI field: 0x" + Integer.toHexString(ci));
        }
        float value = Float.NaN;
        if (application.length >= 5) {
            value = Float.intBitsToFloat(
                    ((application[1] & 0xFF) << 24)
                            | ((application[2] & 0xFF) << 16)
                            | ((application[3] & 0xFF) << 8)
                            | (application[4] & 0xFF));
        }
        return new ParsedTelegram(manufacturer, deviceId, version, deviceType, ci, value);
    }

    public static byte[] readFrame(InputStream in) throws IOException {
        int lField = in.read();
        if (lField < 0) {
            throw new EOFException("EOF reading wM-Bus L-field");
        }
        int appLen = lField - 9;
        if (appLen < 0) {
            throw new IOException("Invalid wM-Bus L-field");
        }
        int dataBlocks = appLen == 0 ? 0 : (appLen + DATA_BLOCK_LEN - 1) / DATA_BLOCK_LEN;
        int total = HEADER_BLOCK_LEN + 2 + appLen + (2 * dataBlocks);
        byte[] frame = new byte[total];
        frame[0] = (byte) lField;
        readFully(in, frame, 1, total - 1);
        return frame;
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

    private static void writeCrcLe(ByteArrayOutputStream out, int crc) {
        out.write(crc & 0xFF);
        out.write((crc >>> 8) & 0xFF);
    }

    private static int readCrcLe(byte[] frame, int offset) {
        return (frame[offset] & 0xFF) | ((frame[offset + 1] & 0xFF) << 8);
    }

    private static void readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int done = 0;
        while (done < length) {
            int read = in.read(buffer, offset + done, length - done);
            if (read < 0) {
                throw new EOFException("EOF reading wM-Bus frame");
            }
            done += read;
        }
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
