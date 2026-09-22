package com.ispf.driver.foundationfieldbus.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Foundation Fieldbus HSE FDA probe used over TCP (not the H1 PHY).
 * <p>
 * Fixed probe documented here (rather than inventing an ASCII dialect): version byte
 * {@code 0x01}, options {@code 0x00}, then a big-endian {@code uint16} length covering any
 * following payload. This is the minimal HSE message header used by this driver.
 */
public final class FoundationFieldbusCodec {

    public static final int VERSION = 0x01;
    public static final int OPTIONS = 0x00;

    private FoundationFieldbusCodec() {
    }

    /**
     * Encode the fixed FDA/HSE probe: {@code version | options | length_be}.
     *
     * @param length big-endian length of any following payload (0 for a header-only probe)
     */
    public static byte[] encodeFdaProbe(int length) {
        if (length < 0 || length > 0xFFFF) {
            throw new IllegalArgumentException("FDA length out of range: " + length);
        }
        return new byte[] {
                (byte) VERSION,
                (byte) OPTIONS,
                (byte) ((length >> 8) & 0xFF),
                (byte) (length & 0xFF)
        };
    }

    public static byte[] encodeFloat(float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putFloat(value);
        return buffer.array();
    }

    public static float decodeFloat(byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new IOException("Foundation Fieldbus value truncated");
        }
        return ByteBuffer.wrap(data, 0, 4).getFloat();
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("Foundation Fieldbus frame truncated");
        }
        return buffer;
    }
}
