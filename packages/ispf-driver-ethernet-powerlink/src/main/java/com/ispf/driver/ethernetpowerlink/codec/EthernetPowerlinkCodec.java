package com.ispf.driver.ethernetpowerlink.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Ethernet POWERLINK basic frame header used on the wire and carried over a TCP gateway.
 * <p>
 * Layout: message type {@code uint8} (SoC {@code 0x01}, PReq {@code 0x03}, PRes {@code 0x04}),
 * destination {@code uint8}, source {@code uint8}, then payload. Not a hard real-time MN.
 */
public final class EthernetPowerlinkCodec {

    public static final int SOC = 0x01;
    public static final int PREQ = 0x03;
    public static final int PRES = 0x04;
    public static final int MN_NODE = 240;

    private EthernetPowerlinkCodec() {
    }

    public static byte[] encodeFrame(int messageType, int destination, int source, byte[] payload) {
        byte[] body = payload == null ? new byte[0] : payload;
        ByteArrayOutputStream out = new ByteArrayOutputStream(3 + body.length);
        out.write(messageType & 0xFF);
        out.write(destination & 0xFF);
        out.write(source & 0xFF);
        out.writeBytes(body);
        return out.toByteArray();
    }

    public static byte[] encodePReq(int destination, int source, byte[] payload) {
        return encodeFrame(PREQ, destination, source, payload);
    }

    public static byte[] encodePRes(int destination, int source, byte[] payload) {
        return encodeFrame(PRES, destination, source, payload);
    }

    public static byte[] encodeFloat(float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putFloat(value);
        return buffer.array();
    }

    public static float decodeFloat(byte[] data, int offset) throws IOException {
        if (data == null || offset + 4 > data.length) {
            throw new IOException("POWERLINK value truncated");
        }
        return ByteBuffer.wrap(data, offset, 4).getFloat();
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("POWERLINK frame truncated");
        }
        return buffer;
    }
}
