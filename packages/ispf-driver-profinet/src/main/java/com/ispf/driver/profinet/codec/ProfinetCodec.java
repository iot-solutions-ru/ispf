package com.ispf.driver.profinet.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * PROFINET DCP Identify Request (Ethernet DCP payload carried on TCP as a gateway).
 * <p>
 * Header-only Identify (DCPDataLength 0): FrameID {@code 0xFEFE}, ServiceID {@code 0x05},
 * ServiceType Request {@code 0x00}, Xid {@code 1}, ResponseDelay {@code 0x0001}.
 * Optional follow-up DCP blocks may carry slot/subslot for a read.
 */
public final class ProfinetCodec {

    public static final int FRAME_ID = 0xFEFE;
    public static final int SERVICE_IDENTIFY = 0x05;
    public static final int SERVICE_TYPE_REQUEST = 0x00;
    public static final int DEFAULT_XID = 1;
    public static final int RESPONSE_DELAY = 0x0001;

    private ProfinetCodec() {
    }

    /** Header-only DCP Identify Request with DCPDataLength 0. */
    public static byte[] encodeIdentifyRequest() {
        return encodeIdentifyHeader(0);
    }

    /**
     * Identify Request whose DCP payload carries a slot/subslot follow-up block
     * (Option {@code 0xFF}, Suboption {@code 0x01}, length 4).
     */
    public static byte[] encodeIdentifyRequest(int slot, int subslot) {
        return encodeIdentifyRequest(slot, subslot, null);
    }

    /**
     * Identify Request with optional IEEE-754 BE float appended in the DCP payload (write).
     */
    public static byte[] encodeIdentifyRequest(int slot, int subslot, float writeValue) {
        return encodeIdentifyRequest(slot, subslot, encodeFloat(writeValue));
    }

    private static byte[] encodeIdentifyRequest(int slot, int subslot, byte[] trailing) {
        byte[] block = new byte[] {
                (byte) 0xFF,
                0x01,
                0x00,
                0x04,
                (byte) ((slot >> 8) & 0xFF),
                (byte) (slot & 0xFF),
                (byte) ((subslot >> 8) & 0xFF),
                (byte) (subslot & 0xFF)
        };
        byte[] extra = trailing == null ? new byte[0] : trailing;
        ByteArrayOutputStream out = new ByteArrayOutputStream(12 + block.length + extra.length);
        out.writeBytes(encodeIdentifyHeader(block.length + extra.length));
        out.writeBytes(block);
        out.writeBytes(extra);
        return out.toByteArray();
    }

    public static byte[] encodeIdentifyHeader(int dcpDataLength) {
        if (dcpDataLength < 0 || dcpDataLength > 0xFFFF) {
            throw new IllegalArgumentException("DCPDataLength out of range: " + dcpDataLength);
        }
        return new byte[] {
                (byte) ((FRAME_ID >> 8) & 0xFF),
                (byte) (FRAME_ID & 0xFF),
                (byte) SERVICE_IDENTIFY,
                (byte) SERVICE_TYPE_REQUEST,
                0x00,
                0x00,
                0x00,
                (byte) (DEFAULT_XID & 0xFF),
                0x00,
                (byte) (RESPONSE_DELAY & 0xFF),
                (byte) ((dcpDataLength >> 8) & 0xFF),
                (byte) (dcpDataLength & 0xFF)
        };
    }

    public static byte[] encodeFloat(float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putFloat(value);
        return buffer.array();
    }

    public static float decodeFloat(byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new IOException("PROFINET value truncated");
        }
        return ByteBuffer.wrap(data, 0, 4).getFloat();
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("PROFINET frame truncated");
        }
        return buffer;
    }
}
