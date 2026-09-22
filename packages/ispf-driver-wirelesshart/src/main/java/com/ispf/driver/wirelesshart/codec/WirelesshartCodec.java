package com.ispf.driver.wirelesshart.codec;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * HART-IP framing used by the WirelessHART gateway path (8-byte header, big-endian).
 * <p>
 * Header: version, message type, message id, status, sequence {@code uint16}, byte count
 * {@code uint16}, then body. Pass-through (message id 3) carries a HART short frame.
 * Not an 802.15.4 WirelessHART radio / HCF stack. Clean-room Apache-2.0, JDK only.
 */
public final class WirelesshartCodec {

    public static final int VERSION = 1;
    public static final int HEADER_LENGTH = 8;

    public static final int MSG_REQUEST = 0;
    public static final int MSG_RESPONSE = 1;
    public static final int MSG_NAK = 15;

    public static final int ID_SESSION_INITIATE = 0;
    public static final int ID_SESSION_CLOSE = 1;
    public static final int ID_KEEP_ALIVE = 2;
    public static final int ID_PASS_THROUGH = 3;

    public static final int CMD_READ_PV = 1;
    public static final int CMD_READ_DYNAMIC = 3;

    /** Literal session initiate for sequence 1: master type 1, inactivity 30000 ms. */
    private static final byte[] SESSION_INITIATE_SEQ1 = new byte[]{
            0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x05,
            0x01, 0x00, 0x00, 0x75, 0x30
    };

    /** Command 1, address 0, empty body + checksum. */
    private static final byte[] HART_CMD1_ADDR0 = new byte[]{0x02, 0x00, 0x01, 0x00, 0x03};

    public static byte[] sessionInitiateSeq1() {
        return SESSION_INITIATE_SEQ1.clone();
    }

    public static byte[] hartCmd1Addr0() {
        return HART_CMD1_ADDR0.clone();
    }

    private WirelesshartCodec() {
    }

    public static byte[] encodeSessionInitiate(int sequence) {
        byte[] payload = new byte[5];
        payload[0] = 0x01;
        payload[1] = 0x00;
        payload[2] = 0x00;
        payload[3] = 0x75;
        payload[4] = 0x30;
        return encodeMessage(MSG_REQUEST, ID_SESSION_INITIATE, 0, sequence, payload);
    }

    public static byte[] encodeSessionInitiateResponse(int sequence) {
        byte[] payload = new byte[5];
        payload[0] = 0x01;
        payload[1] = 0x00;
        payload[2] = 0x00;
        payload[3] = 0x75;
        payload[4] = 0x30;
        return encodeMessage(MSG_RESPONSE, ID_SESSION_INITIATE, 0, sequence, payload);
    }

    public static byte[] encodePassThroughRequest(int sequence, byte[] hartPdu) {
        return encodeMessage(MSG_REQUEST, ID_PASS_THROUGH, 0, sequence, hartPdu);
    }

    public static byte[] encodePassThroughResponse(int sequence, byte[] hartPdu) {
        return encodeMessage(MSG_RESPONSE, ID_PASS_THROUGH, 0, sequence, hartPdu);
    }

    public static byte[] encodeMessage(int messageType, int messageId, int status, int sequence, byte[] payload) {
        byte[] body = payload == null ? new byte[0] : payload;
        if (body.length > 0xFFFF) {
            throw new IllegalArgumentException("HART-IP body too large");
        }
        byte[] frame = new byte[HEADER_LENGTH + body.length];
        frame[0] = (byte) VERSION;
        frame[1] = (byte) (messageType & 0xFF);
        frame[2] = (byte) (messageId & 0xFF);
        frame[3] = (byte) (status & 0xFF);
        frame[4] = (byte) ((sequence >> 8) & 0xFF);
        frame[5] = (byte) (sequence & 0xFF);
        frame[6] = (byte) ((body.length >> 8) & 0xFF);
        frame[7] = (byte) (body.length & 0xFF);
        System.arraycopy(body, 0, frame, HEADER_LENGTH, body.length);
        return frame;
    }

    public static Message decode(byte[] frame) {
        if (frame == null || frame.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("HART-IP frame too short");
        }
        int version = frame[0] & 0xFF;
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported HART-IP version: " + version);
        }
        int messageType = frame[1] & 0xFF;
        int messageId = frame[2] & 0xFF;
        int status = frame[3] & 0xFF;
        int sequence = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
        int byteCount = ((frame[6] & 0xFF) << 8) | (frame[7] & 0xFF);
        if (frame.length < HEADER_LENGTH + byteCount) {
            throw new IllegalArgumentException("Incomplete HART-IP frame");
        }
        byte[] body = Arrays.copyOfRange(frame, HEADER_LENGTH, HEADER_LENGTH + byteCount);
        return new Message(messageType, messageId, status, sequence, body);
    }

    /** Master short-frame command request (polling address). */
    public static byte[] encodeHartCommand(int deviceAddress, int command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        out.write(0x02);
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(0x00);
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    /** Master short-frame write with IEEE float payload. */
    public static byte[] encodeHartWrite(int deviceAddress, int command, float value) {
        ByteArrayOutputStream data = new ByteArrayOutputStream(8);
        writeFloat(data, value);
        byte[] payload = data.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + payload.length);
        out.write(0x02);
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(payload.length & 0xFF);
        out.writeBytes(payload);
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    /**
     * Slave short-frame response for command 1 (PV) or command 3 (dynamic variables).
     */
    public static byte[] encodeHartPvResponse(int deviceAddress, int command, float pv) {
        ByteArrayOutputStream data = new ByteArrayOutputStream(24);
        data.write(0x00);
        data.write(0x00);
        if (command == CMD_READ_DYNAMIC) {
            writeFloat(data, 4.0f);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
        } else {
            data.write(0x27);
            writeFloat(data, pv);
        }
        byte[] payload = data.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + payload.length);
        out.write(0x06);
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(payload.length & 0xFF);
        out.writeBytes(payload);
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    public static HartCommand parseHartCommand(byte[] pdu) {
        if (pdu == null || pdu.length < 5) {
            throw new IllegalArgumentException("HART PDU too short");
        }
        int address = pdu[1] & 0x3F;
        int command = pdu[2] & 0xFF;
        int byteCount = pdu[3] & 0xFF;
        float writeValue = Float.NaN;
        if (byteCount >= 4 && pdu.length >= 4 + byteCount) {
            writeValue = readFloat(pdu, 4);
        }
        return new HartCommand(address, command, byteCount, writeValue);
    }

    public static float extractPv(byte[] responsePdu) {
        if (responsePdu == null || responsePdu.length < 10) {
            throw new IllegalArgumentException("HART response too short for PV");
        }
        int command = responsePdu[2] & 0xFF;
        int dataStart = 4;
        if (command == CMD_READ_DYNAMIC) {
            int pvOffset = dataStart + 2 + 4 + 1;
            return readFloat(responsePdu, pvOffset);
        }
        int pvOffset = dataStart + 2 + 1;
        return readFloat(responsePdu, pvOffset);
    }

    public static int checksum(byte[] bytes) {
        int xor = 0;
        for (byte value : bytes) {
            xor ^= value & 0xFF;
        }
        return xor & 0xFF;
    }

    private static void writeFloat(ByteArrayOutputStream out, float value) {
        int bits = Float.floatToIntBits(value);
        out.write((bits >>> 24) & 0xFF);
        out.write((bits >>> 16) & 0xFF);
        out.write((bits >>> 8) & 0xFF);
        out.write(bits & 0xFF);
    }

    private static float readFloat(byte[] bytes, int offset) {
        int bits = ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
        return Float.intBitsToFloat(bits);
    }

    /** Decoded HART-IP message (body copied defensively). */
    public static final class Message {
        private final int messageType;
        private final int messageId;
        private final int status;
        private final int sequence;
        private final byte[] body;

        Message(int messageType, int messageId, int status, int sequence, byte[] body) {
            this.messageType = messageType;
            this.messageId = messageId;
            this.status = status;
            this.sequence = sequence;
            this.body = body == null ? new byte[0] : body.clone();
        }

        public int messageType() {
            return messageType;
        }

        public int messageId() {
            return messageId;
        }

        public int status() {
            return status;
        }

        public int sequence() {
            return sequence;
        }

        public byte[] body() {
            return body.clone();
        }
    }

    public record HartCommand(int address, int command, int byteCount, float writeValue) {
    }
}
