package com.ispf.driver.hartip.codec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * HART-IP TCP codec: 8-byte big-endian header plus body, and short-frame HART PDUs for pass-through.
 * <p>
 * Header layout: version, message type, message id, status, sequence (uint16), byte count (uint16).
 * Short-frame master request: STX {@code 0x02}, address, command, byte count, XOR checksum.
 * Clean-room Apache-2.0, JDK only — not a full HCF stack and not an FSK modem.
 */
public final class HartIpCodec {

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

    public static final int MASTER_TYPE_PRIMARY = 1;
    public static final int INACTIVITY_TIMER_MS = 30_000;

    private HartIpCodec() {
    }

    public static byte[] encodeSessionInitiate(int sequence) {
        byte[] body = new byte[5];
        body[0] = (byte) MASTER_TYPE_PRIMARY;
        ByteBuffer.wrap(body, 1, 4).order(ByteOrder.BIG_ENDIAN).putInt(INACTIVITY_TIMER_MS);
        return encodeMessage(MSG_REQUEST, ID_SESSION_INITIATE, 0, sequence, body);
    }

    public static byte[] encodeSessionInitiateResponse(int sequence) {
        byte[] body = new byte[5];
        body[0] = (byte) MASTER_TYPE_PRIMARY;
        ByteBuffer.wrap(body, 1, 4).order(ByteOrder.BIG_ENDIAN).putInt(INACTIVITY_TIMER_MS);
        return encodeMessage(MSG_RESPONSE, ID_SESSION_INITIATE, 0, sequence, body);
    }

    public static byte[] encodePassThroughRequest(int sequence, byte[] hartPdu) {
        return encodeMessage(MSG_REQUEST, ID_PASS_THROUGH, 0, sequence, hartPdu);
    }

    public static byte[] encodePassThroughResponse(int sequence, byte[] hartPdu) {
        return encodeMessage(MSG_RESPONSE, ID_PASS_THROUGH, 0, sequence, hartPdu);
    }

    public static byte[] encodeMessage(int messageType, int messageId, int status, int sequence, byte[] payload) {
        byte[] body = payload == null ? new byte[0] : payload;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + body.length).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) VERSION);
        buffer.put((byte) (messageType & 0xFF));
        buffer.put((byte) (messageId & 0xFF));
        buffer.put((byte) (status & 0xFF));
        buffer.putShort((short) (sequence & 0xFFFF));
        buffer.putShort((short) (body.length & 0xFFFF));
        buffer.put(body);
        return buffer.array();
    }

    public static HartIpMessage decode(byte[] frame) {
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
        byte[] payload = Arrays.copyOfRange(frame, HEADER_LENGTH, HEADER_LENGTH + byteCount);
        return new HartIpMessage(messageType, messageId, status, sequence, payload);
    }

    /** Master short-frame command request (polling address). */
    public static byte[] encodeHartCommand(int deviceAddress, int command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5);
        out.write(0x02);
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(0x00);
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    /**
     * Slave short-frame response for command 1 (PV) or command 3 (dynamic variables).
     * Includes response code and device status, then IEEE float PV (and extras for command 3).
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
        return new HartCommand(address, command, byteCount);
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

    public record HartCommand(int address, int command, int byteCount) {
    }
}
