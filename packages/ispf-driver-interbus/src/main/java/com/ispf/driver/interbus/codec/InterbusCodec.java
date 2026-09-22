package com.ispf.driver.interbus.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * INTERBUS process-image framing over TCP: {@code uint16} BE length, then payload.
 * Not the Phoenix INTERBUS master ASIC.
 */
public final class InterbusCodec {

    private InterbusCodec() {
    }

    /** Length-prefix a 2-byte process-image word. */
    public static byte[] encodeProcessImage(int word) {
        int value = word & 0xFFFF;
        return new byte[] {
                0x00,
                0x02,
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    public static byte[] encodeAddressedRead(int slot, int word) {
        return new byte[] {
                0x00,
                0x02,
                (byte) (slot & 0xFF),
                (byte) (word & 0xFF)
        };
    }

    public static byte[] encodeAddressedWrite(int slot, int word, int value) {
        int v = value & 0xFFFF;
        return new byte[] {
                0x00,
                0x04,
                (byte) (slot & 0xFF),
                (byte) (word & 0xFF),
                (byte) ((v >> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
    }

    public static int decodeProcessImageWord(byte[] frame) throws IOException {
        if (frame == null || frame.length < 4) {
            throw new IOException("INTERBUS process image truncated");
        }
        int length = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        if (length != 2 || frame.length < 4) {
            throw new IOException("INTERBUS process image length mismatch: " + length);
        }
        return ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
    }

    public static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("INTERBUS frame truncated");
        }
        return buffer;
    }
}
