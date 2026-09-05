package com.ispf.driver.rockwelldf1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room DF1 protected-mode binary framing for a TCP serial bridge lab.
 * <p>
 * Implements a full-duplex DF1 subset: {@code DLE STX ... DLE ETX BCC} with
 * CMD {@code 0x0F} and FNC {@code 0xA2}/{@code 0xAA} typed logical read/write for
 * N/F/B files. This is <strong>not</strong> a native serial DF1 exclusive-owner
 * stack and <strong>not</strong> EtherNet/IP CIP.
 */
final class RockwellDf1Frame {

    static final byte DLE = 0x10;
    static final byte STX = 0x02;
    static final byte ETX = 0x03;

    static final byte CMD_PROTECTED = 0x0F;
    static final byte FNC_TYPED_READ = (byte) 0xA2;
    static final byte FNC_TYPED_WRITE = (byte) 0xAA;

    static final byte STS_OK = 0x00;

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
        out.write(bcc(pdu) & 0xFF);
        return out.toByteArray();
    }

    /** Classic DF1 BCC: two's complement of the sum of unstuffed PDU bytes. */
    static int bcc(byte[] pdu) {
        int sum = 0;
        for (byte b : pdu) {
            sum = (sum + (b & 0xFF)) & 0xFF;
        }
        return (~sum + 1) & 0xFF;
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
                    int checksum = in.read();
                    if (checksum < 0) {
                        throw new IOException("EOF before BCC");
                    }
                    byte[] raw = toBytes(pdu);
                    if ((checksum & 0xFF) != bcc(raw)) {
                        throw new IOException("DF1 BCC mismatch");
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
        if (pdu.length < 7) {
            throw new IllegalArgumentException("DF1 PDU too short");
        }
        int dst = pdu[0] & 0xFF;
        int src = pdu[1] & 0xFF;
        byte cmd = pdu[2];
        byte sts = pdu[3];
        int tns = (pdu[4] & 0xFF) | ((pdu[5] & 0xFF) << 8);
        byte fnc = pdu.length > 6 ? pdu[6] : 0;
        byte[] payload = pdu.length > 7 ? java.util.Arrays.copyOfRange(pdu, 7, pdu.length) : new byte[0];
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

    record ParsedPdu(int dst, int src, byte cmd, byte sts, int tns, byte fnc, byte[] payload) {
    }
}
