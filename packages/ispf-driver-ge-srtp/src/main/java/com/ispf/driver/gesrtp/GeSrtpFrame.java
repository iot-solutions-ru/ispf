package com.ispf.driver.gesrtp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Clean-room SRTP-lab MAILBOX framing helpers.
 * <p>
 * This is an ISPF lab subset inspired by Emerson/GE SRTP mailbox request/response
 * over TCP (default port 18245). It is <strong>not</strong> a full CPE/SRTP stack:
 * no session negotiation, no multi-segment transfers, no symbolic names, no
 * PLC status/control services — only typed word read/write for %R/%AI/%AQ/%I/%Q.
 */
final class GeSrtpFrame {

    static final byte VERSION = 0x01;
    static final byte CMD_READ = 0x01;
    static final byte CMD_WRITE = 0x02;
    static final byte STATUS_OK = 0x00;

    private GeSrtpFrame() {
    }

    static byte[] buildRequest(byte command, GeSrtpPoint point, int[] writeWords) {
        int dataBytes = writeWords == null ? 0 : writeWords.length * 2;
        ByteBuffer payload = ByteBuffer.allocate(8 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        payload.put(VERSION);
        payload.put(command);
        payload.put(point.memoryType().typeCode());
        payload.put((byte) 0);
        payload.putShort((short) (point.address() & 0xFFFF));
        payload.putShort((short) (point.count() & 0xFFFF));
        if (writeWords != null) {
            for (int word : writeWords) {
                payload.putShort((short) (word & 0xFFFF));
            }
        }
        byte[] body = payload.array();
        ByteBuffer frame = ByteBuffer.allocate(2 + body.length).order(ByteOrder.BIG_ENDIAN);
        frame.putShort((short) body.length);
        frame.put(body);
        return frame.array();
    }

    static ParsedResponse parseResponse(byte[] lengthHeader, byte[] body) {
        if (lengthHeader.length < 2) {
            throw new IllegalArgumentException("SRTP-lab response length header incomplete");
        }
        int length = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);
        if (body.length < length) {
            throw new IllegalArgumentException("SRTP-lab response truncated");
        }
        if (length < 4) {
            throw new IllegalArgumentException("SRTP-lab response too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(body, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        byte version = buf.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unexpected SRTP-lab version " + version);
        }
        byte status = buf.get();
        int count = buf.getShort() & 0xFFFF;
        int[] words = new int[count];
        for (int i = 0; i < count && buf.remaining() >= 2; i++) {
            words[i] = buf.getShort() & 0xFFFF;
        }
        return new ParsedResponse(status, words);
    }

    static byte[] buildOkResponse(int[] words) {
        ByteBuffer payload = ByteBuffer.allocate(4 + words.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        payload.put(VERSION);
        payload.put(STATUS_OK);
        payload.putShort((short) (words.length & 0xFFFF));
        for (int word : words) {
            payload.putShort((short) (word & 0xFFFF));
        }
        byte[] body = payload.array();
        ByteBuffer frame = ByteBuffer.allocate(2 + body.length).order(ByteOrder.BIG_ENDIAN);
        frame.putShort((short) body.length);
        frame.put(body);
        return frame.array();
    }

    static byte[] buildErrorResponse(byte status) {
        ByteBuffer payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        payload.put(VERSION);
        payload.put(status);
        payload.putShort((short) 0);
        byte[] body = payload.array();
        ByteBuffer frame = ByteBuffer.allocate(2 + body.length).order(ByteOrder.BIG_ENDIAN);
        frame.putShort((short) body.length);
        frame.put(body);
        return frame.array();
    }

    record ParsedResponse(byte status, int[] words) {
    }

    record ParsedRequest(byte command, GeSrtpPoint point, int[] writeWords) {
    }

    static ParsedRequest parseRequest(byte[] body) {
        if (body.length < 8) {
            throw new IllegalArgumentException("SRTP-lab request too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        byte version = buf.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unexpected SRTP-lab request version " + version);
        }
        byte command = buf.get();
        byte typeCode = buf.get();
        buf.get(); // reserved
        int address = buf.getShort() & 0xFFFF;
        int count = buf.getShort() & 0xFFFF;
        GeSrtpPoint point = new GeSrtpPoint(GeSrtpPoint.GeSrtpMemoryType.fromTypeCode(typeCode), address, count);
        int[] writeWords = null;
        if (command == CMD_WRITE) {
            writeWords = new int[count];
            for (int i = 0; i < count; i++) {
                if (buf.remaining() < 2) {
                    throw new IllegalArgumentException("SRTP-lab write payload truncated");
                }
                writeWords[i] = buf.getShort() & 0xFFFF;
            }
        }
        return new ParsedRequest(command, point, writeWords);
    }
}
