package com.ispf.driver.zigbee.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Silicon Labs ASH (EZSP UART) framing helpers — not 802.15.4 and not a ZCL stack.
 * <p>
 * CRC is {@code halCommonCrc16} (init {@code 0xFFFF}). Host RST is
 * {@code 1A C0 38 BC 7E} (CANCEL, RST control {@code 0xC0}, CRC high-byte-first, FLAG).
 * Peer RSTACK is {@code C1 02 02 9B 7B 7E}. Clean-room Apache-2.0, JDK only.
 */
public final class AshCodec {

    public static final int CANCEL = 0x1A;
    public static final int FLAG = 0x7E;
    public static final int RST_CONTROL = 0xC0;
    public static final int RSTACK_CONTROL = 0xC1;

    private AshCodec() {
    }

    /**
     * Silicon Labs {@code halCommonCrc16}: init {@code 0xFFFF}, one byte {@code 0xC0} → {@code 0x38BC}.
     */
    public static int crc16(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data");
        }
        return crc16(data, 0, data.length);
    }

    public static int crc16(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("bad crc16 range");
        }
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc = crcStep(crc, data[offset + i] & 0xFF);
        }
        return crc & 0xFFFF;
    }

    /**
     * One {@code halCommonCrc16} byte step from previous {@code crc}.
     */
    public static int crcStep(int crc, int b) {
        crc = ((crc >> 8) | (crc << 8)) & 0xFFFF;
        crc ^= b & 0xFF;
        crc ^= (crc & 0xFF) >> 4;
        crc ^= ((crc << 8) & 0xFFFF) << 4;
        crc ^= (((crc & 0xFF) << 4) & 0xFFFF) << 1;
        return crc & 0xFFFF;
    }

    /**
     * Host reset frame: CANCEL, RST {@code 0xC0}, CRC {@code 0x38BC} (high byte first), FLAG.
     */
    public static byte[] encodeHostReset() {
        byte[] control = {(byte) RST_CONTROL};
        int crc = crc16(control);
        return new byte[] {
                (byte) CANCEL,
                (byte) RST_CONTROL,
                (byte) ((crc >> 8) & 0xFF),
                (byte) (crc & 0xFF),
                (byte) FLAG
        };
    }

    public static void writeHostReset(OutputStream out) throws IOException {
        out.write(encodeHostReset());
        out.flush();
    }

    /**
     * Reads one ASH frame ending at FLAG. Verifies CRC before returning payload (control+data).
     */
    public static byte[] readFramePayload(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) {
                    throw new EOFException("EOF waiting for ASH FLAG");
                }
                throw new EOFException("EOF inside ASH frame");
            }
            if (b == FLAG) {
                break;
            }
            if (b == CANCEL && buf.size() == 0) {
                continue;
            }
            buf.write(b);
        }
        byte[] raw = buf.toByteArray();
        if (raw.length < 3) {
            throw new IOException("ASH frame too short: " + raw.length);
        }
        int dataLen = raw.length - 2;
        int expected = crc16(raw, 0, dataLen);
        int actual = ((raw[dataLen] & 0xFF) << 8) | (raw[dataLen + 1] & 0xFF);
        if (expected != actual) {
            throw new IOException("ASH CRC mismatch: expected 0x"
                    + Integer.toHexString(expected) + " got 0x" + Integer.toHexString(actual));
        }
        byte[] payload = new byte[dataLen];
        System.arraycopy(raw, 0, payload, 0, dataLen);
        return payload;
    }

    /**
     * Parses a verified RSTACK payload {@code C1 | version | reason}.
     */
    public static AshRstack parseRstack(byte[] payload) throws IOException {
        if (payload == null || payload.length < 3) {
            throw new IOException("ASH RSTACK payload too short");
        }
        if ((payload[0] & 0xFF) != RSTACK_CONTROL) {
            throw new IOException("Expected ASH RSTACK control 0xC1, got 0x"
                    + Integer.toHexString(payload[0] & 0xFF));
        }
        return new AshRstack(payload[1] & 0xFF, payload[2] & 0xFF);
    }

    /** RSTACK fields after CRC verification — no {@code byte[]} components. */
    public record AshRstack(int version, int reason) {
    }
}
