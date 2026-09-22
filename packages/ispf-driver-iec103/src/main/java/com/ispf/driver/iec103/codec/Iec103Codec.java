package com.ispf.driver.iec103.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * IEC 60870-5-103 FT1.2 codec (link layer per IEC 60870-5-1).
 * <p>
 * Fixed frame: {@code 10h C A CS 16h}. Variable frame: {@code 68h L L 68h C A ASDU CS 16h}.
 * {@code L} is {@code 2 + ASDU.length}. Checksum is the sum of control, address, and ASDU octets
 * modulo 256. Single-character acknowledge is {@code E5h}.
 * <p>
 * ASDU layout: TYP, VSQ, COT (1), ASDU address (1), then per object FUN, INF, and data.
 */
public final class Iec103Codec {

    public static final int START_VARIABLE = 0x68;
    public static final int START_FIXED = 0x10;
    public static final int END = 0x16;
    public static final int SINGLE_ACK = 0xE5;

    private Iec103Codec() {
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
            throw new IllegalArgumentException("IEC 103 ASDU exceeds FT1.2 length");
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

    public static byte[] encodeAsdu(
            int typeId,
            int cot,
            int asduAddress,
            int fun,
            int inf,
            byte[] information
    ) {
        byte[] extra = information == null ? new byte[0] : information;
        ByteArrayOutputStream out = new ByteArrayOutputStream(6 + extra.length);
        out.write(typeId & 0xFF);
        out.write(0x01);
        out.write(cot & 0xFF);
        out.write(asduAddress & 0xFF);
        out.write(fun & 0xFF);
        out.write(inf & 0xFF);
        out.writeBytes(extra);
        return out.toByteArray();
    }

    public static byte[] encodeInterrogation(int asduAddress) {
        return encodeAsdu(
                Iec103Types.ASDU_GI,
                Iec103Types.COT_GENERAL_INTERROGATION,
                asduAddress,
                0,
                0,
                new byte[] { 0 }
        );
    }

    public static byte[] encodeGeneralCommand(int asduAddress, int fun, int inf, boolean on) {
        byte dco = (byte) (on ? 2 : 1);
        return encodeAsdu(
                Iec103Types.ASDU_GENERAL_COMMAND,
                Iec103Types.COT_COMMAND_ACK,
                asduAddress,
                fun,
                inf,
                new byte[] { dco }
        );
    }

    public static byte[] encodeStatus(int asduAddress, int cot, int fun, int inf, boolean on, int quality) {
        int dpi = (on ? 2 : 1) | (quality & 0xF0);
        return encodeAsdu(Iec103Types.ASDU_TIME_TAGGED, cot, asduAddress, fun, inf, new byte[] { (byte) dpi });
    }

    public static byte[] encodeMeasurandsIi(int asduAddress, int cot, int fun, int inf, float value, int quality) {
        ByteArrayOutputStream info = new ByteArrayOutputStream(5);
        writeFloat(info, value);
        info.write(quality & 0xFF);
        return encodeAsdu(Iec103Types.ASDU_MEASURANDS_II, cot, asduAddress, fun, inf, info.toByteArray());
    }

    public static byte[] encodeMeasFloat(int asduAddress, int cot, int fun, int inf, float value, int quality) {
        ByteArrayOutputStream info = new ByteArrayOutputStream(5);
        writeFloat(info, value);
        info.write(quality & 0xFF);
        return encodeAsdu(Iec103Types.ASDU_MEAS_FLOAT, cot, asduAddress, fun, inf, info.toByteArray());
    }

    public static byte[] encodeGenericData(int asduAddress, int cot, int fun, int inf, float value, int quality) {
        ByteArrayOutputStream info = new ByteArrayOutputStream(5);
        writeFloat(info, value);
        info.write(quality & 0xFF);
        return encodeAsdu(Iec103Types.ASDU_GENERIC_DATA, cot, asduAddress, fun, inf, info.toByteArray());
    }

    public static Iec103Frame decode(byte[] frame) throws IOException {
        if (frame == null || frame.length == 0) {
            throw new IOException("Empty IEC 103 frame");
        }
        int start = frame[0] & 0xFF;
        if (start == SINGLE_ACK) {
            if (frame.length != 1) {
                throw new IOException("IEC 103 E5 acknowledge must be a single octet");
            }
            return Iec103Frame.singleAck();
        }
        if (start == START_FIXED) {
            if (frame.length != 5 || (frame[4] & 0xFF) != END) {
                throw new IOException("IEC 103 fixed frame is malformed");
            }
            int control = frame[1] & 0xFF;
            int address = frame[2] & 0xFF;
            int expected = (control + address) & 0xFF;
            if ((frame[3] & 0xFF) != expected) {
                throw new IOException("IEC 103 fixed frame checksum mismatch");
            }
            return Iec103Frame.fixed(control, address);
        }
        if (start != START_VARIABLE) {
            throw new IOException("IEC 103 unknown start octet: " + start);
        }
        if (frame.length < 6 || (frame[3] & 0xFF) != START_VARIABLE) {
            throw new IOException("IEC 103 variable frame header is malformed");
        }
        int length = frame[1] & 0xFF;
        if ((frame[2] & 0xFF) != length || frame.length != length + 6) {
            throw new IOException("IEC 103 variable frame length mismatch");
        }
        if ((frame[frame.length - 1] & 0xFF) != END) {
            throw new IOException("IEC 103 variable frame missing end octet");
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
            throw new IOException("IEC 103 variable frame checksum mismatch");
        }
        return Iec103Frame.variable(control, address, asdu);
    }

    public static Iec103Frame readFrame(InputStream in) throws IOException {
        int start = in.read();
        if (start < 0) {
            throw new EOFException("IEC 103 connection closed");
        }
        if (start == SINGLE_ACK) {
            return Iec103Frame.singleAck();
        }
        if (start == START_FIXED) {
            byte[] rest = readFully(in, 4);
            byte[] frame = new byte[5];
            frame[0] = (byte) start;
            System.arraycopy(rest, 0, frame, 1, rest.length);
            return decode(frame);
        }
        if (start != START_VARIABLE) {
            throw new IOException("IEC 103 unknown start octet: " + start);
        }
        int length = in.read();
        int lengthRepeat = in.read();
        int secondStart = in.read();
        if (length < 0 || lengthRepeat < 0 || secondStart < 0) {
            throw new EOFException("IEC 103 variable header truncated");
        }
        if (length != lengthRepeat || secondStart != START_VARIABLE) {
            throw new IOException("IEC 103 variable header mismatch");
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

    public static List<Iec103Value> decodeAsdu(byte[] asdu) throws IOException {
        if (asdu == null || asdu.length < 6) {
            throw new IOException("IEC 103 ASDU too short");
        }
        int typeId = asdu[0] & 0xFF;
        int vsq = asdu[1] & 0xFF;
        int count = vsq & 0x7F;
        int offset = 4;
        List<Iec103Value> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ensure(asdu, offset, 2);
            int fun = asdu[offset] & 0xFF;
            int inf = asdu[offset + 1] & 0xFF;
            offset += 2;
            Decoded decoded = decodeInformation(asdu, offset, typeId, fun, inf);
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

    private static Decoded decodeInformation(byte[] asdu, int offset, int typeId, int fun, int inf)
            throws IOException {
        return switch (typeId) {
            case Iec103Types.ASDU_TIME_TAGGED,
                 Iec103Types.ASDU_RELATIVE_TIME_TAGGED,
                 Iec103Types.ASDU_GENERAL_COMMAND -> {
                ensure(asdu, offset, 1);
                int raw = asdu[offset] & 0xFF;
                boolean on = (raw & 0x03) == 2;
                yield new Decoded(
                        new Iec103Value(typeId, fun, inf, on ? 1.0 : 0.0, on, qualityFrom(raw)),
                        offset + 1
                );
            }
            case Iec103Types.ASDU_MEASURANDS_II -> {
                ensure(asdu, offset, 5);
                float value = readFloat(asdu, offset);
                int q = asdu[offset + 4] & 0xFF;
                yield new Decoded(Iec103Value.measurandsIi(fun, inf, value, qualityFrom(q)), offset + 5);
            }
            case Iec103Types.ASDU_GENERIC_DATA -> {
                ensure(asdu, offset, 5);
                float value = readFloat(asdu, offset);
                int q = asdu[offset + 4] & 0xFF;
                yield new Decoded(Iec103Value.genericData(fun, inf, value, qualityFrom(q)), offset + 5);
            }
            case Iec103Types.ASDU_MEAS_FLOAT -> {
                ensure(asdu, offset, 5);
                float value = readFloat(asdu, offset);
                int q = asdu[offset + 4] & 0xFF;
                yield new Decoded(Iec103Value.measured(fun, inf, value, qualityFrom(q)), offset + 5);
            }
            case Iec103Types.ASDU_GI, Iec103Types.ASDU_GI_TERMINATION -> {
                ensure(asdu, offset, 1);
                yield new Decoded(
                        new Iec103Value(typeId, fun, inf, asdu[offset] & 0xFF, false, "GOOD"),
                        offset + 1
                );
            }
            default -> throw new IOException("Unsupported IEC 103 ASDU type: " + typeId);
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

    private static float readFloat(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static void ensure(byte[] data, int offset, int need) throws IOException {
        if (offset + need > data.length) {
            throw new IOException("IEC 103 ASDU truncated at offset " + offset);
        }
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = in.readNBytes(length);
        if (buffer.length != length) {
            throw new EOFException("IEC 103 frame truncated");
        }
        return buffer;
    }

    private record Decoded(Iec103Value value, int next) {
    }
}
