package com.ispf.driver.iolink.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * IO-Link ISDU binary request over a TCP master gateway (not the IO-Link PHY).
 * Layout: port {@code uint8}, index {@code uint16} BE, subindex {@code uint8}.
 */
public final class IoLinkCodec {

    public static final int DEFAULT_INDEX = 0x0010;

    private IoLinkCodec() {
    }

    public static byte[] encodeIsduRead(int port, int index, int subindex) {
        if (port < 0 || port > 255) {
            throw new IllegalArgumentException("IO-Link port out of range: " + port);
        }
        if (index < 0 || index > 0xFFFF) {
            throw new IllegalArgumentException("IO-Link index out of range: " + index);
        }
        if (subindex < 0 || subindex > 255) {
            throw new IllegalArgumentException("IO-Link subindex out of range: " + subindex);
        }
        return new byte[] {
                (byte) port,
                (byte) ((index >> 8) & 0xFF),
                (byte) (index & 0xFF),
                (byte) subindex
        };
    }

    public static byte[] encodeIsduWrite(int port, int index, int subindex, float value) {
        byte[] header = encodeIsduRead(port, index, subindex);
        byte[] body = new byte[8];
        System.arraycopy(header, 0, body, 0, 4);
        byte[] floatBytes = encodeFloat(value);
        System.arraycopy(floatBytes, 0, body, 4, 4);
        return body;
    }

    public static byte[] encodeFloat(float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putFloat(value);
        return buffer.array();
    }

    public static float decodeFloat(byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new IOException("IO-Link value truncated");
        }
        return ByteBuffer.wrap(data, 0, 4).getFloat();
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("IO-Link frame truncated");
        }
        return buffer;
    }
}
