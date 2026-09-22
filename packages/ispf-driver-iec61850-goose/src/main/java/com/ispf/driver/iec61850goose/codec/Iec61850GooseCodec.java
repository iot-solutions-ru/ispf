package com.ispf.driver.iec61850goose.codec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * IEC 61850-8-1 GOOSE header codec (APPID, Length including the 8-byte header,
 * Reserved1, Reserved2, then APDU). Not Ethernet PHY / VLAN tagging.
 */
public final class Iec61850GooseCodec {

    public static final int DEFAULT_APPID = 0x3000;

    public static final byte OP_READ = 0x01;
    public static final byte OP_WRITE = 0x02;
    public static final byte OP_ACK = 0x00;

    private Iec61850GooseCodec() {
    }

    /**
     * Builds a GOOSE PDU: APPID BE, Length BE (header + APDU), reserved zeros, APDU.
     */
    public static byte[] encode(int appId, byte[] apdu) {
        byte[] body = apdu == null ? new byte[0] : apdu;
        int length = 8 + body.length;
        if (length > 0xFFFF) {
            throw new IllegalArgumentException("GOOSE length exceeds uint16");
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

    public static byte[] apdu(byte[] frame) throws IOException {
        ensureHeader(frame);
        int length = length(frame);
        if (length != frame.length) {
            throw new IOException("GOOSE Length " + length + " != frame size " + frame.length);
        }
        return Arrays.copyOfRange(frame, 8, frame.length);
    }

    public static byte[] encodeReadRequest(String wireToken) {
        byte[] name = wireToken.getBytes(StandardCharsets.US_ASCII);
        if (name.length > 255) {
            throw new IllegalArgumentException("GOOSE point name too long");
        }
        byte[] apdu = new byte[2 + name.length];
        apdu[0] = OP_READ;
        apdu[1] = (byte) name.length;
        System.arraycopy(name, 0, apdu, 2, name.length);
        return encode(DEFAULT_APPID, apdu);
    }

    public static byte[] encodeWriteRequest(String wireToken, float value) {
        byte[] name = wireToken.getBytes(StandardCharsets.US_ASCII);
        if (name.length > 255) {
            throw new IllegalArgumentException("GOOSE point name too long");
        }
        byte[] apdu = new byte[2 + name.length + 4];
        apdu[0] = OP_WRITE;
        apdu[1] = (byte) name.length;
        System.arraycopy(name, 0, apdu, 2, name.length);
        ByteBuffer.wrap(apdu, 2 + name.length, 4).order(ByteOrder.BIG_ENDIAN).putFloat(value);
        return encode(DEFAULT_APPID, apdu);
    }

    public static byte[] encodeFloatApdu(float value) {
        byte[] apdu = new byte[4];
        ByteBuffer.wrap(apdu).order(ByteOrder.BIG_ENDIAN).putFloat(value);
        return encode(DEFAULT_APPID, apdu);
    }

    public static byte[] encodeAck() {
        return encode(DEFAULT_APPID, new byte[] { OP_ACK });
    }

    public static float decodeFloatApdu(byte[] frame) throws IOException {
        byte[] apdu = apdu(frame);
        if (apdu.length < 4) {
            throw new IOException("GOOSE APDU too short for float");
        }
        return ByteBuffer.wrap(apdu, 0, 4).order(ByteOrder.BIG_ENDIAN).getFloat();
    }

    public static ParsedRequest parseRequest(byte[] frame) throws IOException {
        byte[] apdu = apdu(frame);
        if (apdu.length < 2) {
            throw new IOException("GOOSE request APDU truncated");
        }
        byte op = apdu[0];
        int nameLen = apdu[1] & 0xFF;
        if (apdu.length < 2 + nameLen) {
            throw new IOException("GOOSE request name truncated");
        }
        String name = new String(apdu, 2, nameLen, StandardCharsets.US_ASCII);
        Float value = null;
        if (op == OP_WRITE) {
            if (apdu.length < 2 + nameLen + 4) {
                throw new IOException("GOOSE write APDU missing float");
            }
            value = ByteBuffer.wrap(apdu, 2 + nameLen, 4).order(ByteOrder.BIG_ENDIAN).getFloat();
        }
        return new ParsedRequest(op, name, value);
    }

    private static void ensureHeader(byte[] frame) throws IOException {
        if (frame == null || frame.length < 8) {
            throw new IOException("GOOSE frame shorter than 8-byte header");
        }
    }

    /** Request decoded from APDU — no {@code byte[]} components. */
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
