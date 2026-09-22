package com.ispf.driver.rockwelldf1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Allen-Bradley DF1 full-duplex framing over a TCP serial bridge.
 * <p>
 * Wire format: {@code DLE STX}, DST, SRC, CMD, STS, TNS lo/hi, data,
 * {@code DLE ETX}, CRC-16 lo/hi. Any {@code 0x10} data byte is stuffed as
 * {@code 10 10}. CMD {@code 0x0F} with FNC {@code 0xA2}/{@code 0xAA} typed
 * logical read/write for N/F/B files. Not a native exclusive-owner serial stack
 * and not EtherNet/IP CIP.
 */
final class RockwellDf1Frame {

    static final byte DLE = 0x10;
    static final byte STX = 0x02;
    static final byte ETX = 0x03;

    static final byte CMD_PROTECTED = 0x0F;
    static final byte FNC_TYPED_READ = (byte) 0xA2;
    static final byte FNC_TYPED_WRITE = (byte) 0xAA;

    static final byte STS_OK = 0x00;

    /** Reflected CRC-16 polynomial used by DF1 full-duplex. */
    private static final int CRC_POLY = 0xA001;

    private RockwellDf1Frame() {
    }

    static byte[] buildTypedRead(int dst, int src, int tns, RockwellDf1Point point) {
        ByteArrayOutputStream pdu = new ByteArrayOutputStream();
        pdu.write(dst & 0xFF);
        pdu.write(src & 0xFF);
        pdu.write(CMD_PROTECTED & 0xFF);
        pdu.write(0x00); // STS
        pdu.write(tns & 0xFF);
        pdu.write((tns >> 8) & 0xFF);
        pdu.write(FNC_TYPED_READ & 0xFF);
        writeAddress(pdu, point);
        pdu.write(elementSize(point)); // size in bytes
        return wrapPdu(pdu.toByteArray());
    }

    static byte[] buildTypedWrite(int dst, int src, int tns, RockwellDf1Point point, byte[] data) {
        ByteArrayOutputStream pdu = new ByteArrayOutputStream();
        pdu.write(dst & 0xFF);
        pdu.write(src & 0xFF);
        pdu.write(CMD_PROTECTED & 0xFF);
        pdu.write(0x00);
        pdu.write(tns & 0xFF);
        pdu.write((tns >> 8) & 0xFF);
        pdu.write(FNC_TYPED_WRITE & 0xFF);
        writeAddress(pdu, point);
        pdu.write(data.length & 0xFF);
        for (byte b : data) {
            pdu.write(b & 0xFF);
        }
        return wrapPdu(pdu.toByteArray());
    }

    private static void writeAddress(ByteArrayOutputStream pdu, RockwellDf1Point point) {
        pdu.write(point.fileNumber() & 0xFF);
        pdu.write(point.fileType().df1Code() & 0xFF);
        pdu.write(point.element() & 0xFF);
        pdu.write(point.bit() & 0xFF);
    }

    static int elementSize(RockwellDf1Point point) {
        return switch (point.fileType()) {
            case N, B -> 2;
            case F -> 4;
        };
    }

    /**
     * Wrap an unstuffed application PDU in a full-duplex DF1 frame.
     * <p>
     * CRC-16 coverage (poly {@code 0xA001}, init {@code 0x0000}): every unstuffed
     * byte from DST through the last data byte, then ETX ({@code 0x03}). Does
     * <strong>not</strong> include the leading DLE STX, the DLE immediately before
     * ETX, stuffed duplicate DLE bytes, or the CRC field itself.
     */
    static byte[] wrapPdu(byte[] pdu) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(DLE);
        out.write(STX);
        for (byte b : pdu) {
            out.write(b & 0xFF);
            if (b == DLE) {
                out.write(DLE); // DLE stuffing
            }
        }
        out.write(DLE);
        out.write(ETX);
        int crc = crc16OverPduAndEtx(pdu);
        out.write(crc & 0xFF);
        out.write((crc >> 8) & 0xFF);
        return out.toByteArray();
    }

    /**
     * DF1 CRC-16 over {@code pdu} bytes followed by ETX (see {@link #wrapPdu}).
     */
    static int crc16OverPduAndEtx(byte[] pdu) {
        int crc = 0x0000;
        for (byte b : pdu) {
            crc = crc16Update(crc, b & 0xFF);
        }
        return crc16Update(crc, ETX & 0xFF);
    }

    static int crc16Update(int crc, int value) {
        int next = crc ^ (value & 0xFF);
        for (int i = 0; i < 8; i++) {
            if ((next & 0x0001) != 0) {
                next = (next >>> 1) ^ CRC_POLY;
            } else {
                next = next >>> 1;
            }
        }
        return next & 0xFFFF;
    }

    static byte[] readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) {
            throw new IOException("EOF before DF1 frame");
        }
        if ((byte) b0 != DLE || (byte) b1 != STX) {
            throw new IOException("Expected DLE STX, got 0x" + Integer.toHexString(b0)
                    + " 0x" + Integer.toHexString(b1));
        }
        List<Byte> pdu = new ArrayList<>();
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("EOF inside DF1 frame");
            }
            if ((byte) b == DLE) {
                int next = in.read();
                if (next < 0) {
                    throw new IOException("EOF after DLE");
                }
                if ((byte) next == DLE) {
                    pdu.add(DLE);
                } else if ((byte) next == ETX) {
                    int crcLo = in.read();
                    int crcHi = in.read();
                    if (crcLo < 0 || crcHi < 0) {
                        throw new IOException("EOF before CRC");
                    }
                    byte[] raw = toBytes(pdu);
                    int expected = crc16OverPduAndEtx(raw);
                    int actual = (crcLo & 0xFF) | ((crcHi & 0xFF) << 8);
                    if (actual != expected) {
                        throw new IOException("DF1 CRC mismatch");
                    }
                    return raw;
                } else {
                    throw new IOException("Unexpected DLE escape 0x" + Integer.toHexString(next));
                }
            } else {
                pdu.add((byte) b);
            }
        }
    }

    private static byte[] toBytes(List<Byte> list) {
        byte[] out = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    static ParsedPdu parsePdu(byte[] pdu) {
        if (pdu.length < 6) {
            throw new IllegalArgumentException("DF1 PDU too short");
        }
        int dst = pdu[0] & 0xFF;
        int src = pdu[1] & 0xFF;
        byte cmd = pdu[2];
        byte sts = pdu[3];
        int tns = (pdu[4] & 0xFF) | ((pdu[5] & 0xFF) << 8);
        boolean reply = (cmd & 0x40) != 0;
        byte fnc;
        byte[] payload;
        if (reply) {
            // Replies carry no FNC; typed-read data starts immediately after TNS.
            fnc = 0;
            payload = pdu.length > 6 ? Arrays.copyOfRange(pdu, 6, pdu.length) : new byte[0];
        } else {
            if (pdu.length < 7) {
                throw new IllegalArgumentException("DF1 request PDU too short");
            }
            fnc = pdu[6];
            payload = pdu.length > 7 ? Arrays.copyOfRange(pdu, 7, pdu.length) : new byte[0];
        }
        return new ParsedPdu(dst, src, cmd, sts, tns, fnc, payload);
    }

    static RockwellDf1Point parseAddress(byte[] payload) {
        if (payload.length < 4) {
            throw new IllegalArgumentException("DF1 address payload too short");
        }
        int fileNumber = payload[0] & 0xFF;
        RockwellDf1Point.FileType type = RockwellDf1Point.FileType.fromDf1Code(payload[1]);
        int element = payload[2] & 0xFF;
        int bit = payload[3] & 0xFF;
        return new RockwellDf1Point(type, fileNumber, element, bit);
    }

    static byte[] buildReply(int dst, int src, int tns, byte sts, byte[] data) {
        ByteArrayOutputStream pdu = new ByteArrayOutputStream();
        pdu.write(dst & 0xFF);
        pdu.write(src & 0xFF);
        pdu.write((CMD_PROTECTED | 0x40) & 0xFF); // reply bit
        pdu.write(sts & 0xFF);
        pdu.write(tns & 0xFF);
        pdu.write((tns >> 8) & 0xFF);
        if (data != null) {
            for (byte b : data) {
                pdu.write(b & 0xFF);
            }
        }
        return wrapPdu(pdu.toByteArray());
    }

    static byte[] encodeInt16(int value) {
        ByteBuffer buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (value & 0xFFFF));
        return buf.array();
    }

    static byte[] encodeFloat(float value) {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putFloat(value);
        return buf.array();
    }

    static int decodeInt16(byte[] data) {
        if (data.length < 2) {
            return 0;
        }
        return (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
    }

    static float decodeFloat(byte[] data) {
        if (data.length < 4) {
            return 0f;
        }
        return ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    /** Parsed application PDU (unstuffed bytes after DLE STX / before DLE ETX). */
    static final class ParsedPdu {
        private final int dst;
        private final int src;
        private final byte cmd;
        private final byte sts;
        private final int tns;
        private final byte fnc;
        private final byte[] payload;

        ParsedPdu(int dst, int src, byte cmd, byte sts, int tns, byte fnc, byte[] payload) {
            this.dst = dst;
            this.src = src;
            this.cmd = cmd;
            this.sts = sts;
            this.tns = tns;
            this.fnc = fnc;
            this.payload = payload == null ? new byte[0] : payload.clone();
        }

        int dst() {
            return dst;
        }

        int src() {
            return src;
        }

        byte cmd() {
            return cmd;
        }

        byte sts() {
            return sts;
        }

        int tns() {
            return tns;
        }

        byte fnc() {
            return fnc;
        }

        byte[] payload() {
            return payload.clone();
        }
    }
}
