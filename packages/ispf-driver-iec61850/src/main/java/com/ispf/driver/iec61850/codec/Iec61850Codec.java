package com.ispf.driver.iec61850.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * TPKT (RFC 1006) + COTP framing and a minimal MMS BER value codec for IEC 61850.
 * <p>
 * BER tags used here:
 * <ul>
 *   <li>{@code 0x1A} — universal VisibleString (definite length), object reference on the wire</li>
 *   <li>{@code 0x87} — MMS FloatingPoint ({@code [APPLICATION 7] IMPLICIT OCTET STRING}):
 *       first content octet is exponent width (8 for IEEE 754 binary32), then 4 float octets BE</li>
 *   <li>{@code 0x30} — universal SEQUENCE wrapping VisibleString + FloatingPoint for writes</li>
 * </ul>
 */
public final class Iec61850Codec {

    /** COTP Connection Request inside TPKT — fixed 22-octet vector. */
    private static final byte[] CONNECTION_REQUEST = new byte[] {
            0x03, 0x00, 0x00, 0x16,
            0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x01, 0x00,
            (byte) 0xC0, 0x01, 0x0A,
            (byte) 0xC1, 0x02, 0x00, 0x01,
            (byte) 0xC2, 0x02, 0x00, 0x02
    };

    private static final int TPKT_VERSION = 0x03;
    private static final int COTP_CC = 0xD0;
    private static final int COTP_DT = 0xF0;
    private static final int COTP_DT_EOT = 0x80;

    private static final int TAG_VISIBLE_STRING = 0x1A;
    private static final int TAG_FLOATING_POINT = 0x87;
    private static final int TAG_SEQUENCE = 0x30;
    private static final int EXPONENT_WIDTH_IEEE = 0x08;

    private Iec61850Codec() {
    }

    public static byte[] connectionRequest() {
        return Arrays.copyOf(CONNECTION_REQUEST, CONNECTION_REQUEST.length);
    }

    /**
     * Minimal COTP CC (PDU type {@code 0xD0}) wrapped in TPKT.
     */
    public static byte[] encodeConnectionConfirm(int destRef, int srcRef) {
        byte[] cotp = new byte[] {
                0x06,
                (byte) COTP_CC,
                (byte) ((destRef >> 8) & 0xFF),
                (byte) (destRef & 0xFF),
                (byte) ((srcRef >> 8) & 0xFF),
                (byte) (srcRef & 0xFF),
                0x00
        };
        return wrapTpkt(cotp);
    }

    /**
     * COTP DT (PDU type {@code 0xF0}, TPDU-NR/EOT {@code 0x80}) inside TPKT.
     */
    public static byte[] encodeDataTransfer(byte[] userData) {
        byte[] body = userData == null ? new byte[0] : userData;
        byte[] cotp = new byte[3 + body.length];
        cotp[0] = 0x02;
        cotp[1] = (byte) COTP_DT;
        cotp[2] = (byte) COTP_DT_EOT;
        System.arraycopy(body, 0, cotp, 3, body.length);
        return wrapTpkt(cotp);
    }

    public static byte[] wrapTpkt(byte[] cotpOrPayload) {
        byte[] body = cotpOrPayload == null ? new byte[0] : cotpOrPayload;
        int length = 4 + body.length;
        if (length > 0xFFFF) {
            throw new IllegalArgumentException("TPKT length exceeds uint16");
        }
        byte[] frame = new byte[length];
        frame[0] = TPKT_VERSION;
        frame[1] = 0x00;
        frame[2] = (byte) ((length >> 8) & 0xFF);
        frame[3] = (byte) (length & 0xFF);
        System.arraycopy(body, 0, frame, 4, body.length);
        return frame;
    }

    public static byte[] readTpkt(InputStream in) throws IOException {
        byte[] header = readFully(in, 4);
        if ((header[0] & 0xFF) != TPKT_VERSION) {
            throw new IOException("TPKT version must be 3, got " + (header[0] & 0xFF));
        }
        if (header[1] != 0x00) {
            throw new IOException("TPKT reserved byte must be 0");
        }
        int length = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        if (length < 4) {
            throw new IOException("TPKT length too short: " + length);
        }
        byte[] frame = new byte[length];
        System.arraycopy(header, 0, frame, 0, 4);
        byte[] rest = readFully(in, length - 4);
        System.arraycopy(rest, 0, frame, 4, rest.length);
        return frame;
    }

    public static void requireConnectionConfirm(byte[] tpktFrame) throws IOException {
        ensure(tpktFrame, 0, 5);
        if ((tpktFrame[0] & 0xFF) != TPKT_VERSION || tpktFrame[1] != 0x00) {
            throw new IOException("COTP CC requires TPKT version 3");
        }
        int li = tpktFrame[4] & 0xFF;
        if (li < 1 || 5 + li > tpktFrame.length) {
            throw new IOException("COTP CC length indicator invalid");
        }
        int pduType = tpktFrame[5] & 0xFF;
        if (pduType != COTP_CC) {
            throw new IOException("Expected COTP CC (0xD0), got 0x"
                    + Integer.toHexString(pduType));
        }
    }

    public static byte[] unwrapDataTransfer(byte[] tpktFrame) throws IOException {
        ensure(tpktFrame, 0, 7);
        if ((tpktFrame[0] & 0xFF) != TPKT_VERSION || tpktFrame[1] != 0x00) {
            throw new IOException("COTP DT requires TPKT version 3");
        }
        int li = tpktFrame[4] & 0xFF;
        if (li < 2) {
            throw new IOException("COTP DT length indicator too short");
        }
        int pduType = tpktFrame[5] & 0xFF;
        if (pduType != COTP_DT) {
            throw new IOException("Expected COTP DT (0xF0), got 0x"
                    + Integer.toHexString(pduType));
        }
        int tpduNr = tpktFrame[6] & 0xFF;
        if (tpduNr != COTP_DT_EOT) {
            throw new IOException("Expected COTP DT EOT TPDU-NR 0x80, got 0x"
                    + Integer.toHexString(tpduNr));
        }
        int userOffset = 4 + 1 + li;
        if (userOffset > tpktFrame.length) {
            throw new IOException("COTP DT user data truncated");
        }
        return Arrays.copyOfRange(tpktFrame, userOffset, tpktFrame.length);
    }

    public static byte[] encodeVisibleString(String value) {
        byte[] chars = value.getBytes(StandardCharsets.US_ASCII);
        return encodeBer(TAG_VISIBLE_STRING, chars);
    }

    public static String decodeVisibleString(byte[] ber) throws IOException {
        BerValue parsed = parseBer(ber, 0);
        if (parsed.tag != TAG_VISIBLE_STRING) {
            throw new IOException("Expected VisibleString tag 0x1A, got 0x"
                    + Integer.toHexString(parsed.tag));
        }
        return new String(parsed.content, StandardCharsets.US_ASCII);
    }

    /**
     * MMS FloatingPoint ({@code 0x87}): exponent-width octet then IEEE 754 binary32 BE.
     */
    public static byte[] encodeFloatingPoint(float value) {
        byte[] content = new byte[5];
        content[0] = EXPONENT_WIDTH_IEEE;
        ByteBuffer.wrap(content, 1, 4).order(ByteOrder.BIG_ENDIAN).putFloat(value);
        return encodeBer(TAG_FLOATING_POINT, content);
    }

    public static float decodeFloatingPoint(byte[] ber) throws IOException {
        BerValue parsed = parseBer(ber, 0);
        if (parsed.tag != TAG_FLOATING_POINT) {
            throw new IOException("Expected FloatingPoint tag 0x87, got 0x"
                    + Integer.toHexString(parsed.tag));
        }
        if (parsed.content.length < 5) {
            throw new IOException("FloatingPoint content too short");
        }
        if ((parsed.content[0] & 0xFF) != EXPONENT_WIDTH_IEEE) {
            throw new IOException("Unsupported FloatingPoint exponent width "
                    + (parsed.content[0] & 0xFF));
        }
        return ByteBuffer.wrap(parsed.content, 1, 4).order(ByteOrder.BIG_ENDIAN).getFloat();
    }

    public static byte[] encodeWritePayload(String reference, float value) {
        byte[] name = encodeVisibleString(reference);
        byte[] number = encodeFloatingPoint(value);
        byte[] content = new byte[name.length + number.length];
        System.arraycopy(name, 0, content, 0, name.length);
        System.arraycopy(number, 0, content, name.length, number.length);
        return encodeBer(TAG_SEQUENCE, content);
    }

    public static WritePayload decodeWritePayload(byte[] ber) throws IOException {
        BerValue seq = parseBer(ber, 0);
        if (seq.tag != TAG_SEQUENCE) {
            throw new IOException("Expected SEQUENCE tag 0x30, got 0x"
                    + Integer.toHexString(seq.tag));
        }
        BerValue name = parseBer(seq.content, 0);
        if (name.tag != TAG_VISIBLE_STRING) {
            throw new IOException("Write SEQUENCE must start with VisibleString");
        }
        BerValue number = parseBer(seq.content, name.nextOffset);
        float value = decodeFloatingPoint(
                Arrays.copyOfRange(seq.content, name.nextOffset, number.nextOffset));
        return new WritePayload(new String(name.content, StandardCharsets.US_ASCII), value);
    }

    private static byte[] encodeBer(int tag, byte[] content) {
        byte[] body = content == null ? new byte[0] : content;
        if (body.length > 127) {
            throw new IllegalArgumentException("BER short-form length limited to 127");
        }
        byte[] out = new byte[2 + body.length];
        out[0] = (byte) tag;
        out[1] = (byte) body.length;
        System.arraycopy(body, 0, out, 2, body.length);
        return out;
    }

    private static BerValue parseBer(byte[] data, int offset) throws IOException {
        ensure(data, offset, 2);
        int tag = data[offset] & 0xFF;
        int length = data[offset + 1] & 0xFF;
        if ((length & 0x80) != 0) {
            throw new IOException("Only definite short-form BER length is supported");
        }
        ensure(data, offset + 2, length);
        byte[] content = Arrays.copyOfRange(data, offset + 2, offset + 2 + length);
        return new BerValue(tag, content, offset + 2 + length);
    }

    private static void ensure(byte[] data, int offset, int need) throws IOException {
        if (data == null || offset < 0 || offset + need > data.length) {
            throw new IOException("BER/TPKT buffer truncated at offset " + offset);
        }
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("TPKT frame truncated");
        }
        return buffer;
    }

    /** Decoded write SEQUENCE (reference + float). No {@code byte[]} components. */
    public static final class WritePayload {
        private final String reference;
        private final float value;

        public WritePayload(String reference, float value) {
            this.reference = reference;
            this.value = value;
        }

        public String reference() {
            return reference;
        }

        public float value() {
            return value;
        }
    }

    private static final class BerValue {
        private final int tag;
        private final byte[] content;
        private final int nextOffset;

        private BerValue(int tag, byte[] content, int nextOffset) {
            this.tag = tag;
            this.content = content;
            this.nextOffset = nextOffset;
        }
    }

    }
