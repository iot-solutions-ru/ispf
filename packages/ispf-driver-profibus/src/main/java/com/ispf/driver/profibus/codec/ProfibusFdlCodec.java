package com.ispf.driver.profibus.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * PROFIBUS FDL framing over a TCP serial-server socket.
 * <p>
 * SD1 fixed: {@code 10h DA SA FC FCS 16h}, FCS = (DA+SA+FC) &amp; 0xFF.
 * SD2 variable: {@code 68h L L 68h DA SA FC PDU FCS 16h}, L = 3 + PDU.length,
 * FCS = sum of DA, SA, FC and PDU octets, mod 256.
 * SD3 fixed data: {@code A2h DA SA FC} + 8 data octets + {@code FCS 16h}.
 */
public final class ProfibusFdlCodec {

    public static final int START_SD1 = 0x10;
    public static final int START_SD2 = 0x68;
    public static final int START_SD3 = 0xA2;
    public static final int END = 0x16;

    private ProfibusFdlCodec() {
    }

    public static byte[] encodeSd1(int da, int sa, int fc) {
        int checksum = (da + sa + fc) & 0xFF;
        return new byte[] {
                (byte) START_SD1,
                (byte) (da & 0xFF),
                (byte) (sa & 0xFF),
                (byte) (fc & 0xFF),
                (byte) checksum,
                (byte) END
        };
    }

    public static byte[] encodeSd2(int da, int sa, int fc, byte[] pdu) {
        byte[] body = pdu == null ? new byte[0] : pdu;
        int length = 3 + body.length;
        if (length > 255) {
            throw new IllegalArgumentException("PROFIBUS FDL SD2 PDU exceeds length");
        }
        int checksum = (da + sa + fc) & 0xFF;
        for (byte value : body) {
            checksum = (checksum + (value & 0xFF)) & 0xFF;
        }
        byte[] frame = new byte[9 + body.length];
        frame[0] = (byte) START_SD2;
        frame[1] = (byte) length;
        frame[2] = (byte) length;
        frame[3] = (byte) START_SD2;
        frame[4] = (byte) (da & 0xFF);
        frame[5] = (byte) (sa & 0xFF);
        frame[6] = (byte) (fc & 0xFF);
        System.arraycopy(body, 0, frame, 7, body.length);
        frame[7 + body.length] = (byte) checksum;
        frame[8 + body.length] = (byte) END;
        return frame;
    }

    public static byte[] encodeSd3(int da, int sa, int fc, byte[] data8) {
        byte[] data = data8 == null ? new byte[8] : data8;
        if (data.length != 8) {
            throw new IllegalArgumentException("PROFIBUS FDL SD3 requires exactly 8 data octets");
        }
        int checksum = (da + sa + fc) & 0xFF;
        for (byte value : data) {
            checksum = (checksum + (value & 0xFF)) & 0xFF;
        }
        byte[] frame = new byte[14];
        frame[0] = (byte) START_SD3;
        frame[1] = (byte) (da & 0xFF);
        frame[2] = (byte) (sa & 0xFF);
        frame[3] = (byte) (fc & 0xFF);
        System.arraycopy(data, 0, frame, 4, 8);
        frame[12] = (byte) checksum;
        frame[13] = (byte) END;
        return frame;
    }

    public static byte[] encodeReadRequest(int slave, int byteOffset) {
        return new byte[] {
                (byte) ProfibusFdlTypes.PDU_RD_REQ,
                (byte) (slave & 0xFF),
                (byte) (byteOffset & 0xFF)
        };
    }

    public static byte[] encodeWriteRequest(int slave, int byteOffset, double value) {
        ByteBuffer buffer = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) ProfibusFdlTypes.PDU_WR_REQ);
        buffer.put((byte) (slave & 0xFF));
        buffer.put((byte) (byteOffset & 0xFF));
        buffer.putDouble(value);
        return buffer.array();
    }

    public static byte[] encodeReadResponse(int slave, int byteOffset, double value) {
        ByteBuffer buffer = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) ProfibusFdlTypes.PDU_RD_RSP);
        buffer.put((byte) (slave & 0xFF));
        buffer.put((byte) (byteOffset & 0xFF));
        buffer.putDouble(value);
        return buffer.array();
    }

    public static byte[] encodeWriteResponse(int slave, int byteOffset) {
        return new byte[] {
                (byte) ProfibusFdlTypes.PDU_WR_RSP,
                (byte) (slave & 0xFF),
                (byte) (byteOffset & 0xFF),
                0x00
        };
    }

    public static byte[] encodeStatusPdu() {
        return new byte[] { (byte) ProfibusFdlTypes.PDU_STATUS };
    }

    public static double decodeReadResponse(byte[] pdu) throws IOException {
        if (pdu == null || pdu.length < 11 || (pdu[0] & 0xFF) != ProfibusFdlTypes.PDU_RD_RSP) {
            throw new IOException("PROFIBUS FDL read response PDU is malformed");
        }
        return ByteBuffer.wrap(pdu, 3, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
    }

    public static void requireWriteResponse(byte[] pdu) throws IOException {
        if (pdu == null || pdu.length < 4 || (pdu[0] & 0xFF) != ProfibusFdlTypes.PDU_WR_RSP) {
            throw new IOException("PROFIBUS FDL write response PDU is malformed");
        }
        if ((pdu[3] & 0xFF) != 0) {
            throw new IOException("PROFIBUS FDL write rejected with status " + (pdu[3] & 0xFF));
        }
    }

    public static ProfibusFdlFrame decode(byte[] frame) throws IOException {
        if (frame == null || frame.length == 0) {
            throw new IOException("Empty PROFIBUS FDL frame");
        }
        int start = frame[0] & 0xFF;
        if (start == START_SD1) {
            if (frame.length != 6 || (frame[5] & 0xFF) != END) {
                throw new IOException("PROFIBUS FDL SD1 frame is malformed");
            }
            int da = frame[1] & 0xFF;
            int sa = frame[2] & 0xFF;
            int fc = frame[3] & 0xFF;
            int expected = (da + sa + fc) & 0xFF;
            if ((frame[4] & 0xFF) != expected) {
                throw new IOException("PROFIBUS FDL SD1 checksum mismatch");
            }
            return ProfibusFdlFrame.sd1(da, sa, fc);
        }
        if (start == START_SD3) {
            if (frame.length != 14 || (frame[13] & 0xFF) != END) {
                throw new IOException("PROFIBUS FDL SD3 frame is malformed");
            }
            int da = frame[1] & 0xFF;
            int sa = frame[2] & 0xFF;
            int fc = frame[3] & 0xFF;
            byte[] data = new byte[8];
            System.arraycopy(frame, 4, data, 0, 8);
            int checksum = (da + sa + fc) & 0xFF;
            for (byte value : data) {
                checksum = (checksum + (value & 0xFF)) & 0xFF;
            }
            if ((frame[12] & 0xFF) != checksum) {
                throw new IOException("PROFIBUS FDL SD3 checksum mismatch");
            }
            return ProfibusFdlFrame.sd3(da, sa, fc, data);
        }
        if (start != START_SD2) {
            throw new IOException("PROFIBUS FDL unknown start octet: " + start);
        }
        if (frame.length < 9 || (frame[3] & 0xFF) != START_SD2) {
            throw new IOException("PROFIBUS FDL SD2 header is malformed");
        }
        int length = frame[1] & 0xFF;
        if ((frame[2] & 0xFF) != length || frame.length != length + 6) {
            throw new IOException("PROFIBUS FDL SD2 length mismatch");
        }
        if ((frame[frame.length - 1] & 0xFF) != END) {
            throw new IOException("PROFIBUS FDL SD2 missing end octet");
        }
        if (length < 3) {
            throw new IOException("PROFIBUS FDL SD2 length too small");
        }
        int da = frame[4] & 0xFF;
        int sa = frame[5] & 0xFF;
        int fc = frame[6] & 0xFF;
        byte[] pdu = new byte[length - 3];
        System.arraycopy(frame, 7, pdu, 0, pdu.length);
        int checksum = (da + sa + fc) & 0xFF;
        for (byte value : pdu) {
            checksum = (checksum + (value & 0xFF)) & 0xFF;
        }
        if ((frame[frame.length - 2] & 0xFF) != checksum) {
            throw new IOException("PROFIBUS FDL SD2 checksum mismatch");
        }
        return ProfibusFdlFrame.sd2(da, sa, fc, pdu);
    }

    public static ProfibusFdlFrame readFrame(InputStream in) throws IOException {
        int start = in.read();
        if (start < 0) {
            throw new EOFException("PROFIBUS FDL connection closed");
        }
        if (start == START_SD1) {
            byte[] rest = readFully(in, 5);
            byte[] frame = new byte[6];
            frame[0] = (byte) start;
            System.arraycopy(rest, 0, frame, 1, rest.length);
            return decode(frame);
        }
        if (start == START_SD3) {
            byte[] rest = readFully(in, 13);
            byte[] frame = new byte[14];
            frame[0] = (byte) start;
            System.arraycopy(rest, 0, frame, 1, rest.length);
            return decode(frame);
        }
        if (start != START_SD2) {
            throw new IOException("PROFIBUS FDL unknown start octet: " + start);
        }
        int length = in.read();
        int lengthRepeat = in.read();
        int secondStart = in.read();
        if (length < 0 || lengthRepeat < 0 || secondStart < 0) {
            throw new EOFException("PROFIBUS FDL SD2 header truncated");
        }
        if (length != lengthRepeat || secondStart != START_SD2) {
            throw new IOException("PROFIBUS FDL SD2 header mismatch");
        }
        byte[] tail = readFully(in, length + 2);
        byte[] frame = new byte[length + 6];
        frame[0] = (byte) start;
        frame[1] = (byte) length;
        frame[2] = (byte) lengthRepeat;
        frame[3] = (byte) secondStart;
        System.arraycopy(tail, 0, frame, 4, tail.length);
        return decode(frame);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("PROFIBUS FDL frame truncated");
        }
        return buffer;
    }
}
