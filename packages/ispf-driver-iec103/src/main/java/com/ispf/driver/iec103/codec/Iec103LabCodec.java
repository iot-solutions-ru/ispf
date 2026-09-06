package com.ispf.driver.iec103.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room IEC103-lab frame codec (Apache-2.0).
 * <p>
 * <strong>Lab subset — not a full IEC 60870-5-103 FT1.2 serial link.</strong>
 * Framing is a simplified APCI+ASDU layout inspired by IEC 60870-5-104 over TCP
 * ({@code 0x68} start, 4-byte control field, ASDU) so unit tests can loopback on
 * port 2404 without FT1.2 / balanced link procedures.
 * <p>
 * Addressing uses protection-style {@code FUN}/{@code INF} (two bytes) rather than
 * 3-byte IEC 104 IOA. Supported ASDUs: {@code 7}/{@code 8} GI, {@code 1} status,
 * {@code 9} measurands, lab {@code 40} float, optional {@code 20} general command.
 * <p>
 * No OpenMUC / GPL IEC stacks.
 */
public final class Iec103LabCodec {

    public static final int START = 0x68;

    private Iec103LabCodec() {
    }

    public static byte[] encodeUFrame(byte[] control4) {
        if (control4.length != 4) {
            throw new IllegalArgumentException("U-frame control must be 4 bytes");
        }
        return new byte[] {
                (byte) START, 0x04,
                control4[0], control4[1], control4[2], control4[3]
        };
    }

    public static byte[] encodeIFrame(int sendSeq, int recvSeq, byte[] asdu) {
        byte[] apdu = new byte[6 + asdu.length];
        apdu[0] = (byte) START;
        apdu[1] = (byte) (4 + asdu.length);
        apdu[2] = (byte) ((sendSeq << 1) & 0xFE);
        apdu[3] = (byte) ((sendSeq >> 7) & 0xFF);
        apdu[4] = (byte) ((recvSeq << 1) & 0xFE);
        apdu[5] = (byte) ((recvSeq >> 7) & 0xFF);
        System.arraycopy(asdu, 0, apdu, 6, asdu.length);
        return apdu;
    }

    public static byte[] encodeInterrogation(int commonAddress) throws IOException {
        return encodeAsdu(Iec103LabTypes.ASDU_GI, Iec103LabTypes.COT_ACTIVATION, commonAddress, 0, 0,
                new byte[] { 0 });
    }

    public static byte[] encodeGeneralCommand(int commonAddress, int fun, int inf, boolean on)
            throws IOException {
        // DCO-style: 1=OFF, 2=ON (lab)
        byte dco = (byte) (on ? 2 : 1);
        return encodeAsdu(Iec103LabTypes.ASDU_GENERAL_COMMAND, Iec103LabTypes.COT_ACTIVATION,
                commonAddress, fun, inf, new byte[] { dco });
    }

    public static byte[] encodeStatus(int commonAddress, int cot, int fun, int inf, boolean on, int quality)
            throws IOException {
        int dpi = (on ? 2 : 1) | (quality & 0xF0);
        return encodeAsdu(Iec103LabTypes.ASDU_TIME_TAGGED, cot, commonAddress, fun, inf,
                new byte[] { (byte) dpi });
    }

    public static byte[] encodeMeasurandsIi(int commonAddress, int cot, int fun, int inf, float value, int quality)
            throws IOException {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        writeFloat32(info, value);
        info.write(quality & 0xFF);
        return encodeAsdu(Iec103LabTypes.ASDU_MEASURANDS_II, cot, commonAddress, fun, inf, info.toByteArray());
    }

    public static byte[] encodeLabMeasFloat(int commonAddress, int cot, int fun, int inf, float value, int quality)
            throws IOException {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        writeFloat32(info, value);
        info.write(quality & 0xFF);
        return encodeAsdu(Iec103LabTypes.ASDU_LAB_MEAS_FLOAT, cot, commonAddress, fun, inf, info.toByteArray());
    }

    public static byte[] encodeAsdu(
            int typeId,
            int cot,
            int commonAddress,
            int fun,
            int inf,
            byte[] information
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(typeId & 0xFF);
        out.write(1); // VSQ: one object
        writeLittle16(out, cot & 0xFFFF);
        writeLittle16(out, commonAddress & 0xFFFF);
        out.write(fun & 0xFF);
        out.write(inf & 0xFF);
        out.write(information);
        return out.toByteArray();
    }

    public static ParsedApdu parseApdu(byte[] frame) throws IOException {
        if (frame.length < 6 || (frame[0] & 0xFF) != START) {
            throw new IOException("IEC103-lab APDU missing 0x68 start");
        }
        int length = frame[1] & 0xFF;
        if (frame.length < 2 + length) {
            throw new IOException("IEC103-lab APDU truncated");
        }
        int ctrl0 = frame[2] & 0xFF;
        if ((ctrl0 & 0x03) == 0x03) {
            return new ParsedApdu(ApduKind.U, ctrl0, List.of(), frame);
        }
        if ((ctrl0 & 0x01) == 0x01) {
            return new ParsedApdu(ApduKind.S, ctrl0, List.of(), frame);
        }
        byte[] asdu = new byte[length - 4];
        System.arraycopy(frame, 6, asdu, 0, asdu.length);
        return new ParsedApdu(ApduKind.I, ctrl0, decodeAsdu(asdu), frame);
    }

    public static List<Iec103LabValue> decodeAsdu(byte[] asdu) throws IOException {
        if (asdu.length < 6) {
            throw new IOException("IEC103-lab ASDU too short");
        }
        int typeId = asdu[0] & 0xFF;
        int vsq = asdu[1] & 0xFF;
        int count = vsq & 0x7F;
        int offset = 6;
        List<Iec103LabValue> values = new ArrayList<>();
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

    private static Decoded decodeInformation(byte[] asdu, int offset, int typeId, int fun, int inf)
            throws IOException {
        return switch (typeId) {
            case Iec103LabTypes.ASDU_TIME_TAGGED, Iec103LabTypes.ASDU_GENERAL_COMMAND -> {
                ensure(asdu, offset, 1);
                int raw = asdu[offset] & 0xFF;
                boolean on = (raw & 0x03) == 2;
                yield new Decoded(Iec103LabValue.status(fun, inf, on, qualityFrom(raw)), offset + 1);
            }
            case Iec103LabTypes.ASDU_MEASURANDS_II -> {
                ensure(asdu, offset, 5);
                float value = readFloat32(asdu, offset);
                int q = asdu[offset + 4] & 0xFF;
                yield new Decoded(Iec103LabValue.measurandsIi(fun, inf, value, qualityFrom(q)), offset + 5);
            }
            case Iec103LabTypes.ASDU_LAB_MEAS_FLOAT -> {
                ensure(asdu, offset, 5);
                float value = readFloat32(asdu, offset);
                int q = asdu[offset + 4] & 0xFF;
                yield new Decoded(Iec103LabValue.measured(fun, inf, value, qualityFrom(q)), offset + 5);
            }
            case Iec103LabTypes.ASDU_GI, Iec103LabTypes.ASDU_GI_TERMINATION -> {
                ensure(asdu, offset, 1);
                yield new Decoded(new Iec103LabValue(typeId, fun, inf, asdu[offset] & 0xFF, false, "GOOD"),
                        offset + 1);
            }
            default -> throw new IOException("Unsupported IEC103-lab ASDU type: " + typeId);
        };
    }

    public static int asduTypeId(byte[] asdu) {
        return asdu.length == 0 ? -1 : asdu[0] & 0xFF;
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

    private static void writeLittle16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeFloat32(ByteArrayOutputStream out, float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(value);
        out.writeBytes(buffer.array());
    }

    private static float readFloat32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static void ensure(byte[] data, int offset, int need) throws IOException {
        if (offset + need > data.length) {
            throw new IOException("IEC103-lab ASDU truncated at offset " + offset);
        }
    }

    public enum ApduKind {
        I, S, U
    }

    public record ParsedApdu(ApduKind kind, int ctrl0, List<Iec103LabValue> values, byte[] raw) {
        public byte[] asdu() {
            if (kind != ApduKind.I || raw.length < 6) {
                return new byte[0];
            }
            int length = raw[1] & 0xFF;
            byte[] asdu = new byte[length - 4];
            System.arraycopy(raw, 6, asdu, 0, asdu.length);
            return asdu;
        }
    }

    private record Decoded(Iec103LabValue value, int next) {
    }
}
