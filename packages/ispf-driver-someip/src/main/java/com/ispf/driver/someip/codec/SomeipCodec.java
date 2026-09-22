package com.ispf.driver.someip.codec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * AUTOSAR SOME/IP header codec (16-byte big-endian header + payload).
 * <p>
 * Layout: serviceId, methodId, length (bytes after the length field = 8 + payload),
 * clientId, sessionId, protocolVersion=1, interfaceVersion=1, messageType, returnCode.
 * Message types: request {@code 0x00}, response {@code 0x80}, request-no-return {@code 0x01}.
 */
public final class SomeipCodec {

    public static final byte MSG_REQUEST = 0x00;
    public static final byte MSG_REQUEST_NO_RETURN = 0x01;
    public static final byte MSG_RESPONSE = (byte) 0x80;
    public static final byte PROTOCOL_VERSION = 0x01;
    public static final byte INTERFACE_VERSION = 0x01;
    public static final byte E_OK = 0x00;

    private SomeipCodec() {
    }

    public static byte[] encodeFrame(
            int service,
            int method,
            int clientId,
            int sessionId,
            byte messageType,
            byte returnCode,
            byte[] payload
    ) {
        byte[] body = payload == null ? new byte[0] : payload;
        int length = 8 + body.length;
        ByteBuffer buf = ByteBuffer.allocate(16 + body.length);
        buf.putShort((short) service);
        buf.putShort((short) method);
        buf.putInt(length);
        buf.putShort((short) clientId);
        buf.putShort((short) sessionId);
        buf.put(PROTOCOL_VERSION);
        buf.put(INTERFACE_VERSION);
        buf.put(messageType);
        buf.put(returnCode);
        buf.put(body);
        return buf.array();
    }

    public static SomeipFrame decodeFrame(byte[] frame) {
        if (frame == null || frame.length < 16) {
            throw new IllegalArgumentException("SOME/IP frame too short: "
                    + (frame == null ? -1 : frame.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(frame);
        int service = buf.getShort() & 0xFFFF;
        int method = buf.getShort() & 0xFFFF;
        int length = buf.getInt();
        int client = buf.getShort() & 0xFFFF;
        int session = buf.getShort() & 0xFFFF;
        byte protocol = buf.get();
        byte iface = buf.get();
        byte messageType = buf.get();
        byte returnCode = buf.get();
        if (protocol != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unexpected SOME/IP protocol version: " + protocol);
        }
        int payloadLen = length - 8;
        if (payloadLen < 0 || 16 + payloadLen > frame.length) {
            throw new IllegalArgumentException("Invalid SOME/IP length: " + length);
        }
        String payloadHex = toHex(frame, 16, payloadLen);
        return new SomeipFrame(service, method, client, session, iface, messageType, returnCode, payloadHex);
    }

    public static byte[] readTcpFrame(InputStream in) throws IOException {
        byte[] header = readFully(in, 16);
        int length = ByteBuffer.wrap(header, 4, 4).getInt();
        int payloadLen = length - 8;
        if (payloadLen < 0 || payloadLen > 65536) {
            throw new IOException("Invalid SOME/IP TCP length: " + length);
        }
        if (payloadLen == 0) {
            return header;
        }
        byte[] payload = readFully(in, payloadLen);
        byte[] frame = new byte[16 + payloadLen];
        System.arraycopy(header, 0, frame, 0, 16);
        System.arraycopy(payload, 0, frame, 16, payloadLen);
        return frame;
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new IOException("EOF reading SOME/IP TCP frame");
            }
            offset += n;
        }
        return buf;
    }

    public static byte[] fromHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return new byte[0];
        }
        String clean = hex.replace(" ", "").toUpperCase(Locale.ROOT);
        if (clean.regionMatches(true, 0, "0X", 0, 2)) {
            clean = clean.substring(2);
        }
        if ((clean.length() % 2) != 0 || !clean.matches("[0-9A-F]*")) {
            throw new IllegalArgumentException("Invalid hex payload: " + hex);
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String toHex(byte[] data) {
        return toHex(data, 0, data == null ? 0 : data.length);
    }

    public static String toHex(byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(String.format(Locale.ROOT, "%02X", data[offset + i] & 0xFF));
        }
        return sb.toString();
    }

    public static String tryUtf8(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        String text = new String(data, StandardCharsets.UTF_8);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                return null;
            }
        }
        return text;
    }

    /**
     * Decoded SOME/IP header. Payload is hex text so the record has no {@code byte[]} component.
     */
    public record SomeipFrame(
            int service,
            int method,
            int clientId,
            int sessionId,
            byte interfaceVersion,
            byte messageType,
            byte returnCode,
            String payloadHex
    ) {
        public byte[] payload() {
            return fromHex(payloadHex);
        }
    }
}
