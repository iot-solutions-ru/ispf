package com.ispf.driver.hartip.codec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Minimal HART-IP TCP lab codec (session + pass-through universal command 1/3 style).
 * <p>
 * Not a full HCF HART-IP stack and not an FSK modem. Clean-room Apache-2.0, JDK only.
 */
public final class HartIpLabCodec {

    public static final int VERSION = 1;
    public static final int MSG_REQUEST = 0;
    public static final int MSG_RESPONSE = 1;
    public static final int MSG_NAK = 15;

    public static final int ID_SESSION_INITIATE = 0;
    public static final int ID_SESSION_CLOSE = 1;
    public static final int ID_KEEP_ALIVE = 2;
    public static final int ID_PASS_THROUGH = 3;

    public static final int CMD_READ_PV = 1;
    public static final int CMD_READ_DYNAMIC = 3;

    private HartIpLabCodec() {
    }

    public static byte[] encodeSessionInitiate(int sequence) {
        // masterType=1 (primary), inactivityCloseTimer=30000 ms
        byte[] payload = new byte[5];
        payload[0] = 0x01;
        ByteBuffer.wrap(payload, 1, 4).order(ByteOrder.BIG_ENDIAN).putInt(30_000);
        return encodeMessage(MSG_REQUEST, ID_SESSION_INITIATE, 0, sequence, payload);
    }

    public static byte[] encodeSessionInitiateResponse(int sequence) {
        byte[] payload = new byte[5];
        payload[0] = 0x01;
        ByteBuffer.wrap(payload, 1, 4).order(ByteOrder.BIG_ENDIAN).putInt(30_000);
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
        ByteBuffer buffer = ByteBuffer.allocate(10 + body.length).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) VERSION);
        buffer.put((byte) (messageType & 0xFF));
        buffer.put((byte) (messageId & 0xFF));
        buffer.put((byte) (status & 0xFF));
        buffer.putInt(sequence);
        buffer.putShort((short) (body.length & 0xFFFF));
        buffer.put(body);
        return buffer.array();
    }

    public static HartIpMessage decode(byte[] frame) {
        if (frame == null || frame.length < 10) {
            throw new IllegalArgumentException("HART-IP frame too short");
        }
        int version = frame[0] & 0xFF;
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported HART-IP version: " + version);
        }
        int messageType = frame[1] & 0xFF;
        int messageId = frame[2] & 0xFF;
        int status = frame[3] & 0xFF;
        int sequence = ByteBuffer.wrap(frame, 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        int byteCount = ((frame[8] & 0xFF) << 8) | (frame[9] & 0xFF);
        if (frame.length < 10 + byteCount) {
            throw new IllegalArgumentException("Incomplete HART-IP frame");
        }
        byte[] payload = Arrays.copyOfRange(frame, 10, 10 + byteCount);
        return new HartIpMessage(messageType, messageId, status, sequence, payload);
    }

    /** Master short-frame command request (polling address). */
    public static byte[] encodeHartCommand(int deviceAddress, int command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        out.write(0x02); // short-frame master STX
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(0x00); // byte count
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    /**
     * Slave short-frame response for command 1 (PV) or command 3 (dynamic vars lab).
     * Includes response code + device status then IEEE float PV (and optional extras for cmd 3).
     */
    public static byte[] encodeHartPvResponse(int deviceAddress, int command, float pv) {
        ByteArrayOutputStream data = new ByteArrayOutputStream(24);
        data.write(0x00); // response code
        data.write(0x00); // device status
        if (command == CMD_READ_DYNAMIC) {
            writeFloat(data, 4.0f); // loop current mA (lab)
            data.write(0x27); // units code (lab: degrees C)
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
        } else {
            data.write(0x27); // units
            writeFloat(data, pv);
        }
        byte[] payload = data.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + payload.length);
        out.write(0x06); // short-frame slave STX
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
        int dataStart = 4; // after delim, addr, cmd, count — data includes rc+status
        if (command == CMD_READ_DYNAMIC) {
            // rc, status, current(4), units(1), pv(4)
            int pvOffset = dataStart + 2 + 4 + 1;
            return readFloat(responsePdu, pvOffset);
        }
        // cmd 1: rc, status, units(1), pv(4)
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

    public record HartIpMessage(int messageType, int messageId, int status, int sequence, byte[] payload) {
    }

    public record HartCommand(int address, int command, int byteCount) {
    }
}
