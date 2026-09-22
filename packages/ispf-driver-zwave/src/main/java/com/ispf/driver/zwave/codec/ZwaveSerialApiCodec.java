package com.ispf.driver.zwave.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Z-Wave Serial API framing for serial-over-TCP (not RF).
 * <p>
 * Frame: {@code SOF (0x01) | Length | Type | FuncID | params… | Checksum}.
 * Length counts itself plus Type, FuncID and params (excludes SOF and checksum).
 * Checksum is the XOR of every byte after SOF except the checksum byte, then XOR {@code 0xFF}.
 * ACK is the single byte {@code 0x06}.
 * <p>
 * Clean-room Apache-2.0, JDK only — not chipmaker SDK / Z-Wave RF PHY.
 */
public final class ZwaveSerialApiCodec {

    public static final int SOF = 0x01;
    public static final int ACK = 0x06;
    public static final int TYPE_REQUEST = 0x00;
    public static final int TYPE_RESPONSE = 0x01;

    /** Serial API: get controller library / protocol version. */
    public static final int FUNC_ID_ZW_GET_VERSION = 0x15;

    /** Serial API: get node protocol info (capability / basic / generic / specific). */
    public static final int FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO = 0x41;

    private ZwaveSerialApiCodec() {
    }

    /**
     * Handwritten {@link #FUNC_ID_ZW_GET_VERSION} request:
     * {@code 01 03 00 15 E9}.
     */
    public static byte[] getVersionRequestLiteral() {
        return new byte[] {
                (byte) 0x01, 0x03, 0x00, 0x15, (byte) 0xE9
        };
    }

    /**
     * Handwritten {@link #FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO} request for node 1:
     * {@code 01 04 00 41 01 BB}.
     */
    public static byte[] getNodeProtocolInfoRequestLiteral(int nodeId) {
        if (nodeId == 1) {
            return new byte[] {
                    (byte) 0x01, 0x04, 0x00, 0x41, 0x01, (byte) 0xBB
            };
        }
        return encodeRequest(FUNC_ID_ZW_GET_NODE_PROTOCOL_INFO, (byte) (nodeId & 0xFF));
    }

    public static byte[] encodeRequest(int funcId, byte... params) {
        byte[] payload = params == null ? new byte[0] : params;
        int length = 1 + 1 + 1 + payload.length; // length + type + func + params
        byte[] frame = new byte[1 + length + 1];
        frame[0] = (byte) SOF;
        frame[1] = (byte) length;
        frame[2] = (byte) TYPE_REQUEST;
        frame[3] = (byte) (funcId & 0xFF);
        System.arraycopy(payload, 0, frame, 4, payload.length);
        frame[frame.length - 1] = (byte) checksum(frame, 1, frame.length - 1);
        return frame;
    }

    public static byte[] encodeResponse(int funcId, byte... params) {
        byte[] payload = params == null ? new byte[0] : params;
        int length = 1 + 1 + 1 + payload.length;
        byte[] frame = new byte[1 + length + 1];
        frame[0] = (byte) SOF;
        frame[1] = (byte) length;
        frame[2] = (byte) TYPE_RESPONSE;
        frame[3] = (byte) (funcId & 0xFF);
        System.arraycopy(payload, 0, frame, 4, payload.length);
        frame[frame.length - 1] = (byte) checksum(frame, 1, frame.length - 1);
        return frame;
    }

    /**
     * XOR of bytes in {@code frame[from..toExclusive)}, then XOR {@code 0xFF}.
     */
    public static int checksum(byte[] frame, int from, int toExclusive) {
        int xor = 0;
        for (int i = from; i < toExclusive; i++) {
            xor ^= frame[i] & 0xFF;
        }
        return (xor ^ 0xFF) & 0xFF;
    }

    public static void writeAck(OutputStream out) throws IOException {
        out.write(ACK);
        out.flush();
    }

    public static void writeFrame(OutputStream out, byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    public static void readAck(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("EOF waiting for Z-Wave Serial API ACK");
        }
        if (b != ACK) {
            throw new IOException("Expected Z-Wave Serial API ACK 0x06, got 0x"
                    + Integer.toHexString(b & 0xFF));
        }
    }

    public static byte[] readFrame(InputStream in) throws IOException {
        int sof;
        do {
            sof = in.read();
            if (sof < 0) {
                throw new EOFException("EOF waiting for Z-Wave Serial API SOF");
            }
        } while (sof != SOF);

        int length = in.read();
        if (length < 0) {
            throw new EOFException("EOF reading Z-Wave Serial API length");
        }
        if (length < 3) {
            throw new IOException("Z-Wave Serial API length too small: " + length);
        }
        byte[] frame = new byte[1 + length + 1];
        frame[0] = (byte) SOF;
        frame[1] = (byte) length;
        // Length includes the length byte: after it come (length - 1) body bytes + checksum.
        int afterLength = length;
        int offset = 2;
        while (afterLength > 0) {
            int n = in.read(frame, offset, afterLength);
            if (n < 0) {
                throw new EOFException("EOF reading Z-Wave Serial API frame body");
            }
            offset += n;
            afterLength -= n;
        }
        int expected = checksum(frame, 1, frame.length - 1);
        int actual = frame[frame.length - 1] & 0xFF;
        if (expected != actual) {
            throw new IOException("Z-Wave Serial API checksum mismatch: expected 0x"
                    + Integer.toHexString(expected) + " got 0x" + Integer.toHexString(actual));
        }
        return frame;
    }

    public static int functionId(byte[] frame) {
        if (frame == null || frame.length < 4) {
            throw new IllegalArgumentException("frame too short");
        }
        return frame[3] & 0xFF;
    }

    public static int frameType(byte[] frame) {
        if (frame == null || frame.length < 3) {
            throw new IllegalArgumentException("frame too short");
        }
        return frame[2] & 0xFF;
    }

    public static byte[] params(byte[] frame) {
        if (frame == null || frame.length < 5) {
            return new byte[0];
        }
        return Arrays.copyOfRange(frame, 4, frame.length - 1);
    }
}
