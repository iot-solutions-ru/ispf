package com.ispf.driver.devicenet.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * CIP-ish explicit messaging for DeviceNet over TCP (not the DeviceNet CAN PHY).
 * Get_Attribute_Single service {@code 0x0E} with an 8-bit logical path.
 */
public final class DeviceNetCodec {

    public static final int GET_ATTRIBUTE_SINGLE = 0x0E;
    public static final int SET_ATTRIBUTE_SINGLE = 0x10;
    public static final int GET_ATTRIBUTE_SINGLE_REPLY = 0x8E;

    private DeviceNetCodec() {
    }

    public static byte[] encodeGetAttributeSingle(int cipClass, int instance, int attribute) {
        return encodeExplicit(GET_ATTRIBUTE_SINGLE, cipClass, instance, attribute, null);
    }

    public static byte[] encodeSetAttributeSingle(int cipClass, int instance, int attribute, float value) {
        return encodeExplicit(SET_ATTRIBUTE_SINGLE, cipClass, instance, attribute, encodeFloat(value));
    }

    private static byte[] encodeExplicit(
            int service, int cipClass, int instance, int attribute, byte[] data) {
        if (cipClass < 0 || cipClass > 0xFF || instance < 0 || instance > 0xFF
                || attribute < 0 || attribute > 0xFF) {
            throw new IllegalArgumentException(
                    "CIP path requires 8-bit class/instance/attribute");
        }
        byte[] extra = data == null ? new byte[0] : data;
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + extra.length);
        out.write(service & 0xFF);
        out.write(0x03); // path size in words
        out.write(0x20); // 8-bit class segment
        out.write(cipClass & 0xFF);
        out.write(0x24); // 8-bit instance segment
        out.write(instance & 0xFF);
        out.write(0x30); // 8-bit attribute segment
        out.write(attribute & 0xFF);
        out.writeBytes(extra);
        return out.toByteArray();
    }

    public static byte[] encodeFloat(float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putFloat(value);
        return buffer.array();
    }

    public static float decodeFloat(byte[] data, int offset) throws IOException {
        if (data == null || offset + 4 > data.length) {
            throw new IOException("DeviceNet value truncated");
        }
        return ByteBuffer.wrap(data, offset, 4).getFloat();
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("DeviceNet frame truncated");
        }
        return buffer;
    }
}
