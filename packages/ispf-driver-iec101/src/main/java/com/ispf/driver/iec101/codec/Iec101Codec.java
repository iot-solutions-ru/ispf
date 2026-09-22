package com.ispf.driver.iec101.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * IEC 60870-5-101 FT1.2 codec for the unbalanced profile used by {@code iec101}.
 * <p>
 * Fixed frame: {@code 10h C A CS 16h}. Variable frame: {@code 68h L L 68h C A ASDU CS 16h}.
 * {@code L} counts the control field, the link address and the ASDU. Checksum is the
 * arithmetic sum of those same octets, modulo 256. Single-character acknowledge is {@code E5h}.
 * <p>
 * ASDU layout is the common structure size: cause of transmission 1 byte, common address
 * 2 bytes little-endian, information-object address 2 bytes little-endian. Short floats
 * ({@code M_ME_NC_1}, {@code C_SE_NC_1}) are IEEE 754 little-endian.
 */
public final class Iec101Codec {

    public static final int START_VARIABLE = 0x68;
    public static final int START_FIXED = 0x10;
    public static final int END = 0x16;
    public static final int SINGLE_ACK = 0xE5;

    private Iec101Codec() {
    }

    /** Primary control field: PRM=1, optional FCB/FCV, function in the low nibble. */
    public static int primaryControl(int function, boolean fcv, boolean fcb) {
        int control = 0x40 | (function & 0x0F);
        if (fcv) {
            control |= 0x10;
        }
        if (fcb) {
            control |= 0x20;
        }
        return control;
    }

    public static int functionCode(int control) {
        return control & 0x0F;
    }

    public static byte[] encodeFixed(int control, int linkAddress) {
        int checksum = (control + (linkAddress & 0xFF)) & 0xFF;
        return new byte[] {
                (byte) START_FIXED,
                (byte) control,
                (byte) linkAddress,
                (byte) checksum,
                (byte) END
        };
    }

    public static byte[] encodeVariable(int control, int linkAddress, byte[] asdu) {
        byte[] body = asdu == null ? new byte[0] : asdu;
        int length = 2 + body.length;
        if (length > 255) {
            throw new IllegalArgumentException("IEC 101 ASDU exceeds FT1.2 length");
        }
        int checksum = (control + (linkAddress & 0xFF)) & 0xFF;
        for (byte value : body) {
            checksum = (checksum + (value & 0xFF)) & 0xFF;
        }
        byte[] frame = new byte[8 + body.length];
        frame[0] = (byte) START_VARIABLE;
        frame[1] = (byte) length;
        frame[2] = (byte) length;
        frame[3] = (byte) START_VARIABLE;
        frame[4] = (byte) control;
        frame[5] = (byte) linkAddress;
        System.arraycopy(body, 0, frame, 6, body.length);
        frame[6 + body.length] = (byte) checksum;
        frame[7 + body.length] = (byte) END;
        return frame;
    }

    public static byte[] encodeAsdu(int typeId, int cot, int commonAddress, int ioa, byte[] info) {
        byte[] extra = info == null ? new byte[0] : info;
        ByteArrayOutputStream out = new ByteArrayOutputStream(6 + extra.length);
        out.write(typeId & 0xFF);
        out.write(0x01);
        out.write(cot & 0xFF);
        out.write(commonAddress & 0xFF);
        out.write((commonAddress >>> 8) & 0xFF);
        out.write(ioa & 0xFF);
        out.write((ioa >>> 8) & 0xFF);
        out.writeBytes(extra);
        return out.toByteArray();
    }

    public static byte[] encodeInterrogation(int commonAddress) {
        return encodeAsdu(
                Iec101Types.C_IC_NA_1,
                Iec101Types.COT_ACTIVATION,
                commonAddress,
                0,
                new byte[] { (byte) Iec101Types.QOI_STATION }
        );
    }

    public static byte[] encodeSingleCommand(int commonAddress, int ioa, boolean on) {
        return encodeAsdu(
                Iec101Types.C_SC_NA_1,
                Iec101Types.COT_ACTIVATION,
                commonAddress,
                ioa,
                new byte[] { (byte) (on ? 1 : 0) }
        );
    }

    public static byte[] encodeSetpointFloat(int commonAddress, int ioa, float value) {
        ByteArrayOutputStream info = new ByteArrayOutputStream(5);
        writeFloat(info, value);
        info.write(0);
        return encodeAsdu(Iec101Types.C_SE_NC_1, Iec101Types.COT_ACTIVATION, commonAddress, ioa, info.toByteArray());
    }

    public static byte[] encodeMeasuredFloat(int commonAddress, int cot, int ioa, float value, int quality) {
        ByteArrayOutputStream info = new ByteArrayOutputStream(5);
        writeFloat(info, value);
        info.write(quality & 0xFF);
        return encodeAsdu(Iec101Types.M_ME_NC_1, cot, commonAddress, ioa, info.toByteArray());
    }

    public static byte[] encodeSinglePoint(int commonAddress, int cot, int ioa, boolean on, int quality) {
        int siq = (on ? 1 : 0) | (quality & 0xFE);
        return encodeAsdu(Iec101Types.M_SP_NA_1, cot, commonAddress, ioa, new byte[] { (byte) siq });
    }

    public static Iec101Frame decode(byte[] frame) throws IOException {
        if (frame == null || frame.length == 0) {
            throw new IOException("Empty IEC 101 frame");
        }
        int start = frame[0] & 0xFF;
        if (start == SINGLE_ACK) {
            if (frame.length != 1) {
                throw new IOException("IEC 101 E5 acknowledge must be a single octet");
            }
            return Iec101Frame.singleAck();
        }
        if (start == START_FIXED) {
            if (frame.length != 5 || (frame[4] & 0xFF) != END) {
                throw new IOException("IEC 101 fixed frame is malformed");
            }
            int control = frame[1] & 0xFF;
            int address = frame[2] & 0xFF;
            int expected = (control + address) & 0xFF;
            if ((frame[3] & 0xFF) != expected) {
                throw new IOException("IEC 101 fixed frame checksum mismatch");
            }
            return Iec101Frame.fixed(control, address);
        }
        if (start != START_VARIABLE) {
            throw new IOException("IEC 101 unknown start octet: " + start);
        }
        if (frame.length < 6 || (frame[3] & 0xFF) != START_VARIABLE) {
            throw new IOException("IEC 101 variable frame header is malformed");
        }
        int length = frame[1] & 0xFF;
        if ((frame[2] & 0xFF) != length || frame.length != length + 6) {
            throw new IOException("IEC 101 variable frame length mismatch");
        }
        if ((frame[frame.length - 1] & 0xFF) != END) {
            throw new IOException("IEC 101 variable frame missing end octet");
        }
        int control = frame[4] & 0xFF;
        int address = frame[5] & 0xFF;
        byte[] asdu = new byte[length - 2];
        System.arraycopy(frame, 6, asdu, 0, asdu.length);
        int checksum = (control + address) & 0xFF;
        for (byte value : asdu) {
            checksum = (checksum + (value & 0xFF)) & 0xFF;
        }
        if ((frame[frame.length - 2] & 0xFF) != checksum) {
            throw new IOException("IEC 101 variable frame checksum mismatch");
        }
        return Iec101Frame.variable(control, address, asdu);
    }

    public static Iec101Frame readFrame(InputStream in) throws IOException {
        int start = in.read();
        if (start < 0) {
            throw new EOFException("IEC 101 connection closed");
        }
        if (start == SINGLE_ACK) {
            return Iec101Frame.singleAck();
        }
        if (start == START_FIXED) {
            byte[] rest = readFully(in, 4);
            byte[] frame = new byte[5];
            frame[0] = (byte) start;
            System.arraycopy(rest, 0, frame, 1, rest.length);
            return decode(frame);
        }
        if (start != START_VARIABLE) {
            throw new IOException("IEC 101 unknown start octet: " + start);
        }
        int length = in.read();
        int lengthRepeat = in.read();
        int secondStart = in.read();
        if (length < 0 || lengthRepeat < 0 || secondStart < 0) {
            throw new EOFException("IEC 101 variable header truncated");
        }
        if (length != lengthRepeat || secondStart != START_VARIABLE) {
            throw new IOException("IEC 101 variable header mismatch");
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

    public static List<Iec101Value> decodeAsdu(byte[] asdu) throws IOException {
        if (asdu == null || asdu.length < 7) {
            throw new IOException("IEC 101 ASDU too short");
        }
        int typeId = asdu[0] & 0xFF;
        int vsq = asdu[1] & 0xFF;
        int cot = asdu[2] & 0x3F;
        int count = vsq & 0x7F;
        boolean sequence = (vsq & 0x80) != 0;
        int offset = 5;
        List<Iec101Value> values = new ArrayList<>();
        int baseIoa = -1;
        for (int index = 0; index < count; index++) {
            int ioa;
            if (sequence && index > 0) {
                ioa = baseIoa + index;
            } else {
                ensure(asdu, offset, 2);
                ioa = (asdu[offset] & 0xFF) | ((asdu[offset + 1] & 0xFF) << 8);
                offset += 2;
                if (index == 0) {
                    baseIoa = ioa;
                }
            }
            Decoded decoded = readObject(asdu, offset, typeId, ioa, cot);
            values.add(decoded.value());
            offset = decoded.next();
        }
        return values;
    }

    public static int asduTypeId(byte[] asdu) {
        return asdu == null || asdu.length == 0 ? -1 : asdu[0] & 0xFF;
    }

    public static int asduCause(byte[] asdu) {
        return asdu == null || asdu.length < 3 ? -1 : asdu[2] & 0x3F;
    }

    private static Decoded readObject(byte[] asdu, int offset, int typeId, int ioa, int cot) throws IOException {
        return switch (typeId) {
            case Iec101Types.M_SP_NA_1, Iec101Types.C_SC_NA_1 -> {
                ensure(asdu, offset, 1);
                int raw = asdu[offset] & 0xFF;
                yield new Decoded(
                        new Iec101Value(typeId, ioa, (raw & 0x01) == 0 ? 0.0 : 1.0, (raw & 0x01) != 0, qualityFrom(raw)),
                        offset + 1
                );
            }
            case Iec101Types.M_ME_NC_1, Iec101Types.C_SE_NC_1 -> {
                ensure(asdu, offset, 5);
                float value = ByteBuffer.wrap(asdu, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                int quality = asdu[offset + 4] & 0xFF;
                String qualityText = typeId == Iec101Types.C_SE_NC_1 ? "GOOD" : qualityFrom(quality);
                yield new Decoded(new Iec101Value(typeId, ioa, value, false, qualityText), offset + 5);
            }
            case Iec101Types.C_IC_NA_1 -> {
                ensure(asdu, offset, 1);
                yield new Decoded(
                        new Iec101Value(typeId, ioa, asdu[offset] & 0xFF, false, "GOOD"),
                        offset + 1
                );
            }
            default -> throw new IOException("Unsupported IEC 101 ASDU type " + typeId + " (COT " + cot + ")");
        };
    }

    private static String qualityFrom(int raw) {
        if ((raw & 0x80) != 0) {
            return "INVALID";
        }
        if ((raw & 0x40) != 0) {
            return "NOT_TOPICAL";
        }
        return "GOOD";
    }

    private static void writeFloat(ByteArrayOutputStream out, float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(value);
        out.writeBytes(buffer.array());
    }

    private static void ensure(byte[] data, int offset, int need) throws IOException {
        if (offset + need > data.length) {
            throw new IOException("IEC 101 ASDU truncated at offset " + offset);
        }
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("IEC 101 frame truncated");
        }
        return buffer;
    }

    private record Decoded(Iec101Value value, int next) {
    }
}
