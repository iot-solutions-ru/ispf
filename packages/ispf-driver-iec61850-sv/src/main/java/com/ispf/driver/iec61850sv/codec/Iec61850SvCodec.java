package com.ispf.driver.iec61850sv.codec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * IEC 61850-9-2 SAV header codec (APPID, Length including the 8-byte header,
 * Reserved1, Reserved2, then ASDU).
 */
public final class Iec61850SvCodec {

    public static final int DEFAULT_APPID = 0x4000;

    public static final byte OP_READ = 0x01;
    public static final byte OP_WRITE = 0x02;
    public static final byte OP_ACK = 0x00;

    private Iec61850SvCodec() {
    }

    /**
     * Builds a SAV PDU: APPID BE, Length BE (header + ASDU), reserved zeros, ASDU.
     */
    public static byte[] encode(int appId, byte[] asdu) {
        byte[] body = asdu == null ? new byte[0] : asdu;
        int length = 8 + body.length;
        if (length > 0xFFFF) {
            throw new IllegalArgumentException("SV length exceeds uint16");
        }
        byte[] frame = new byte[length];
        frame[0] = (byte) ((appId >> 8) & 0xFF);
        frame[1] = (byte) (appId & 0xFF);
        frame[2] = (byte) ((length >> 8) & 0xFF);
        frame[3] = (byte) (length & 0xFF);
        frame[4] = 0x00;
        frame[5] = 0x00;
        frame[6] = 0x00;
        frame[7] = 0x00;
        System.arraycopy(body, 0, frame, 8, body.length);
        return frame;
    }

    public static int appId(byte[] frame) throws IOException {
        ensureHeader(frame);
        return ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
    }

    public static int length(byte[] frame) throws IOException {
        ensureHeader(frame);
        return ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
    }

    public static byte[] asdu(byte[] frame) throws IOException {
        ensureHeader(frame);
        int length = length(frame);
        if (length != frame.length) {
            throw new IOException("SV Length " + length + " != frame size " + frame.length);
        }
        return Arrays.copyOfRange(frame, 8, frame.length);
    }

    public static byte[] encodeReadRequest(String wireToken) {
        byte[] name = wireToken.getBytes(StandardCharsets.US_ASCII);
        if (name.length > 255) {
            throw new IllegalArgumentException("SV point name too long");
        }
        byte[] asdu = new byte[2 + name.length];
        asdu[0] = OP_READ;
        asdu[1] = (byte) name.length;
        System.arraycopy(name, 0, asdu, 2, name.length);
        return encode(DEFAULT_APPID, asdu);
    }

    public static byte[] encodeWriteRequest(String wireToken, float value) {
        byte[] name = wireToken.getBytes(StandardCharsets.US_ASCII);
        if (name.length > 255) {
            throw new IllegalArgumentException("SV point name too long");
        }
        byte[] asdu = new byte[2 + name.length + 4];
        asdu[0] = OP_WRITE;
        asdu[1] = (byte) name.length;
        System.arraycopy(name, 0, asdu, 2, name.length);
        ByteBuffer.wrap(asdu, 2 + name.length, 4).order(ByteOrder.BIG_ENDIAN).putFloat(value);
        return encode(DEFAULT_APPID, asdu);
    }

    public static byte[] encodeFloatAsdu(float value) {
        byte[] asdu = new byte[4];
        ByteBuffer.wrap(asdu).order(ByteOrder.BIG_ENDIAN).putFloat(value);
        return encode(DEFAULT_APPID, asdu);
    }

    public static byte[] encodeAck() {
        return encode(DEFAULT_APPID, new byte[] { OP_ACK });
    }

    public static float decodeFloatAsdu(byte[] frame) throws IOException {
        byte[] asdu = asdu(frame);
        if (asdu.length < 4) {
            throw new IOException("SV ASDU too short for float");
        }
        return ByteBuffer.wrap(asdu, 0, 4).order(ByteOrder.BIG_ENDIAN).getFloat();
    }

    public static ParsedRequest parseRequest(byte[] frame) throws IOException {
        byte[] asdu = asdu(frame);
        if (asdu.length < 2) {
            throw new IOException("SV request ASDU truncated");
        }
        byte op = asdu[0];
        int nameLen = asdu[1] & 0xFF;
        if (asdu.length < 2 + nameLen) {
            throw new IOException("SV request name truncated");
        }
        String name = new String(asdu, 2, nameLen, StandardCharsets.US_ASCII);
        Float value = null;
        if (op == OP_WRITE) {
            if (asdu.length < 2 + nameLen + 4) {
                throw new IOException("SV write ASDU missing float");
            }
            value = ByteBuffer.wrap(asdu, 2 + nameLen, 4).order(ByteOrder.BIG_ENDIAN).getFloat();
        }
        return new ParsedRequest(op, name, value);
    }

    private static void ensureHeader(byte[] frame) throws IOException {
        if (frame == null || frame.length < 8) {
            throw new IOException("SV frame shorter than 8-byte header");
        }
    }

    /** Request decoded from ASDU — no {@code byte[]} components. */
    public static final class ParsedRequest {
        private final byte op;
        private final String name;
        private final Float value;

        public ParsedRequest(byte op, String name, Float value) {
            this.op = op;
            this.name = name;
            this.value = value;
        }

        public byte op() {
            return op;
        }

        public String name() {
            return name;
        }

        public Float value() {
            return value;
        }
    }
}
