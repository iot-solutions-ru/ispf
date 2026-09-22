package com.ispf.driver.asinterface.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * AS-Interface master call over TCP: two bytes carrying a 5-bit address (0–31) and
 * command/data. Not the yellow-cable AS-i PHY.
 */
public final class AsInterfaceCodec {

    private AsInterfaceCodec() {
    }

    public static byte[] encodeMasterCall(int address, int data) {
        if (address < 0 || address > 31) {
            throw new IllegalArgumentException("AS-Interface address out of range: " + address);
        }
        if (data < 0 || data > 255) {
            throw new IllegalArgumentException("AS-Interface data out of range: " + data);
        }
        return new byte[] { (byte) address, (byte) data };
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("AS-Interface frame truncated");
        }
        return buffer;
    }
}
