package com.ispf.driver.rockwellcsp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Clean-room CSP (PCCC-over-Ethernet) <strong>lab framing</strong> for typed N/F/B read/write.
 * <p>
 * <strong>Honesty:</strong> this is an ISPF CSP-lab subset on TCP port 2222 — documented binary
 * framing that carries PCCC-shaped typed logical addresses. It is <strong>not</strong> EtherNet/IP
 * CIP, <strong>not</strong> DF1 serial (DLE/STX), and <strong>not</strong> a full CSPv4 / SLC/PLC-5
 * Ethernet stack. Frames are length-prefixed with a fixed lab header so loopback and drivers agree.
 * <pre>
 *   0..1  magic 0x4353 ("CS")
 *   2     version = 1
 *   3     cmd: 0x01 typed-read, 0x02 typed-write, 0x81/0x82 reply
 *   4..5  transaction id (BE)
 *   6..7  payload length (BE)
 *   8..   payload
 * </pre>
 * Typed-read payload: fileNumber, fileTypeCode, element(BE u16), bit, size.
 * Typed-write payload: same address fields + size + data.
 * Reply payload: sts + optional data.
 */
final class RockwellCspFrame {

    static final short MAGIC = 0x4353; // "CS"
    static final byte VERSION = 1;

    static final byte CMD_TYPED_READ = 0x01;
    static final byte CMD_TYPED_WRITE = 0x02;
    static final byte CMD_TYPED_READ_REPLY = (byte) 0x81;
    static final byte CMD_TYPED_WRITE_REPLY = (byte) 0x82;

    static final byte STS_OK = 0x00;

    private RockwellCspFrame() {
    }

    static byte[] buildTypedRead(int tns, RockwellCspPoint point) {
        ByteBuffer payload = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN);
        payload.put((byte) (point.fileNumber() & 0xFF));
        payload.put(point.fileType().pcccCode());
        payload.putShort((short) (point.element() & 0xFFFF));
        payload.put((byte) (point.bit() & 0xFF));
        payload.put((byte) elementSize(point));
        return wrap(CMD_TYPED_READ, tns, payload.array());
    }

    static byte[] buildTypedWrite(int tns, RockwellCspPoint point, byte[] data) {
        ByteBuffer payload = ByteBuffer.allocate(6 + data.length).order(ByteOrder.BIG_ENDIAN);
        payload.put((byte) (point.fileNumber() & 0xFF));
        payload.put(point.fileType().pcccCode());
        payload.putShort((short) (point.element() & 0xFFFF));
        payload.put((byte) (point.bit() & 0xFF));
        payload.put((byte) (data.length & 0xFF));
        payload.put(data);
        return wrap(CMD_TYPED_WRITE, tns, payload.array());
    }

    static byte[] buildReply(byte requestCmd, int tns, byte sts, byte[] data) {
        byte replyCmd = requestCmd == CMD_TYPED_WRITE ? CMD_TYPED_WRITE_REPLY : CMD_TYPED_READ_REPLY;
        byte[] payload = new byte[1 + (data == null ? 0 : data.length)];
        payload[0] = sts;
        if (data != null && data.length > 0) {
            System.arraycopy(data, 0, payload, 1, data.length);
        }
        return wrap(replyCmd, tns, payload);
    }

    static byte[] wrap(byte cmd, int tns, byte[] payload) {
        ByteBuffer frame = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.BIG_ENDIAN);
        frame.putShort(MAGIC);
        frame.put(VERSION);
        frame.put(cmd);
        frame.putShort((short) (tns & 0xFFFF));
        frame.putShort((short) payload.length);
        frame.put(payload);
        return frame.array();
    }

    static int elementSize(RockwellCspPoint point) {
        return switch (point.fileType()) {
            case N, B -> 2;
            case F -> 4;
        };
    }

    static ParsedFrame readFrame(InputStream in) throws IOException {
        byte[] header = in.readNBytes(8);
        if (header.length < 8) {
            throw new IOException("EOF before CSP-lab header");
        }
        ByteBuffer hdr = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        short magic = hdr.getShort();
        if (magic != MAGIC) {
            throw new IOException("Expected CSP magic 0x4353, got 0x" + Integer.toHexString(magic & 0xFFFF));
        }
        byte version = hdr.get();
        if (version != VERSION) {
            throw new IOException("Unsupported CSP-lab version " + (version & 0xFF));
        }
        byte cmd = hdr.get();
        int tns = hdr.getShort() & 0xFFFF;
        int length = hdr.getShort() & 0xFFFF;
        byte[] payload = length == 0 ? new byte[0] : in.readNBytes(length);
        if (payload.length < length) {
            throw new IOException("Truncated CSP-lab payload");
        }
        return new ParsedFrame(cmd, tns, payload);
    }

    static RockwellCspPoint parseAddress(byte[] payload) {
        if (payload.length < 5) {
            throw new IllegalArgumentException("CSP address payload too short");
        }
        int fileNumber = payload[0] & 0xFF;
        RockwellCspPoint.FileType type = RockwellCspPoint.FileType.fromPcccCode(payload[1]);
        int element = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
        int bit = payload[4] & 0xFF;
        return new RockwellCspPoint(type, fileNumber, element, bit);
    }

    static byte[] encodeInt16(int value) {
        ByteBuffer buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (value & 0xFFFF));
        return buf.array();
    }

    static byte[] encodeFloat(float value) {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putFloat(value);
        return buf.array();
    }

    static int decodeInt16(byte[] data) {
        if (data.length < 2) {
            return 0;
        }
        return (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
    }

    static float decodeFloat(byte[] data) {
        if (data.length < 4) {
            return 0f;
        }
        return ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    static byte[] replyData(byte[] replyPayload) {
        if (replyPayload.length <= 1) {
            return new byte[0];
        }
        return Arrays.copyOfRange(replyPayload, 1, replyPayload.length);
    }

    record ParsedFrame(byte cmd, int tns, byte[] payload) {
    }
}
