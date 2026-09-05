package com.ispf.driver.iec101.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room IEC101-lab frame codec (Apache-2.0).
 * <p>
 * <strong>Lab subset — not a full IEC 60870-5-101 balanced serial link.</strong>
 * Framing is a simplified APCI+ASDU layout inspired by IEC 60870-5-104 over TCP
 * ({@code 0x68} start, 4-byte control field, ASDU) so unit tests can loopback on
 * port 2404 without FT1.2 / balanced link procedures.
 * <p>
 * Differences vs real IEC 60870-5-101 serial:
 * <ul>
 *   <li>No FT1.2 fixed/variable frames, no link-layer primary/secondary state machine</li>
 *   <li>ASDU uses 2-byte COT and 3-byte IOA (104-style) rather than configurable 101 sizes</li>
 *   <li>Supported ASDUs only: {@code C_IC_NA_1}, {@code M_ME_NC_1}, {@code M_SP_NA_1},
 *       optional {@code C_SC_NA_1} / {@code C_SE_NC_1}</li>
 * </ul>
 * No OpenMUC / GPL IEC stacks.
 */
public final class Iec101LabCodec {

    public static final int START = 0x68;

    private Iec101LabCodec() {
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
        return encodeAsdu(Iec101LabTypes.C_IC_NA_1, Iec101LabTypes.COT_ACTIVATION, commonAddress, 0,
                new byte[] { 20 });
    }

    public static byte[] encodeSingleCommand(int commonAddress, int ioa, boolean on) throws IOException {
        return encodeAsdu(Iec101LabTypes.C_SC_NA_1, Iec101LabTypes.COT_ACTIVATION, commonAddress, ioa,
                new byte[] { (byte) (on ? 1 : 0) });
    }

    public static byte[] encodeSetpointFloat(int commonAddress, int ioa, float value) throws IOException {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        writeFloat32(info, value);
        info.write(0); // qualifier
        return encodeAsdu(Iec101LabTypes.C_SE_NC_1, Iec101LabTypes.COT_ACTIVATION, commonAddress, ioa,
                info.toByteArray());
    }

    public static byte[] encodeMeasuredFloat(int commonAddress, int cot, int ioa, float value, int quality)
            throws IOException {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        writeFloat32(info, value);
        info.write(quality & 0xFF);
        return encodeAsdu(Iec101LabTypes.M_ME_NC_1, cot, commonAddress, ioa, info.toByteArray());
    }

    public static byte[] encodeSinglePoint(int commonAddress, int cot, int ioa, boolean on, int quality)
            throws IOException {
        int siq = (on ? 1 : 0) | (quality & 0xF0);
        return encodeAsdu(Iec101LabTypes.M_SP_NA_1, cot, commonAddress, ioa, new byte[] { (byte) siq });
    }

    public static byte[] encodeAsdu(int typeId, int cot, int commonAddress, int ioa, byte[] information)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(typeId & 0xFF);
        out.write(1); // VSQ: one object, non-sequence
        writeLittle16(out, cot & 0xFFFF);
        writeLittle16(out, commonAddress & 0xFFFF);
        writeIoa(out, ioa);
        out.write(information);
        return out.toByteArray();
    }

    /**
     * Parse one APDU starting at {@code 0x68}. Returns payload kind and contents.
     */
    public static ParsedApdu parseApdu(byte[] frame) throws IOException {
        if (frame.length < 6 || (frame[0] & 0xFF) != START) {
            throw new IOException("IEC101-lab APDU missing 0x68 start");
        }
        int length = frame[1] & 0xFF;
        if (frame.length < 2 + length) {
            throw new IOException("IEC101-lab APDU truncated");
        }
        int ctrl0 = frame[2] & 0xFF;
        if ((ctrl0 & 0x03) == 0x03) {
            // U-format
            return new ParsedApdu(ApduKind.U, ctrl0, List.of(), frame);
        }
        if ((ctrl0 & 0x01) == 0x01) {
            return new ParsedApdu(ApduKind.S, ctrl0, List.of(), frame);
        }
        byte[] asdu = new byte[length - 4];
        System.arraycopy(frame, 6, asdu, 0, asdu.length);
        return new ParsedApdu(ApduKind.I, ctrl0, decodeAsdu(asdu), frame);
    }

    public static List<Iec101LabValue> decodeAsdu(byte[] asdu) throws IOException {
        if (asdu.length < 6) {
            throw new IOException("IEC101-lab ASDU too short");
        }
        int typeId = asdu[0] & 0xFF;
        int vsq = asdu[1] & 0xFF;
        int count = vsq & 0x7F;
        boolean sequence = (vsq & 0x80) != 0;
        int offset = 6;
        List<Iec101LabValue> values = new ArrayList<>();
        int baseIoa = -1;
        for (int i = 0; i < count; i++) {
            int ioa;
            if (sequence && i > 0) {
                ioa = baseIoa + i;
            } else {
                ensure(asdu, offset, 3);
                ioa = little24(asdu, offset);
                offset += 3;
                if (i == 0) {
                    baseIoa = ioa;
                }
            }
            Decoded decoded = decodeInformation(asdu, offset, typeId, ioa);
            values.add(decoded.value());
            offset = decoded.next();
        }
        return values;
    }

    private static Decoded decodeInformation(byte[] asdu, int offset, int typeId, int ioa) throws IOException {
        return switch (typeId) {
            case Iec101LabTypes.M_SP_NA_1, Iec101LabTypes.C_SC_NA_1 -> {
                ensure(asdu, offset, 1);
                int raw = asdu[offset] & 0xFF;
                yield new Decoded(Iec101LabValue.singlePoint(ioa, (raw & 0x01) != 0, qualityFrom(raw)), offset + 1);
            }
            case Iec101LabTypes.M_ME_NC_1 -> {
                ensure(asdu, offset, 5);
                float value = readFloat32(asdu, offset);
                int q = asdu[offset + 4] & 0xFF;
                yield new Decoded(Iec101LabValue.measured(ioa, value, qualityFrom(q)), offset + 5);
            }
            case Iec101LabTypes.C_SE_NC_1 -> {
                ensure(asdu, offset, 5);
                float value = readFloat32(asdu, offset);
                yield new Decoded(Iec101LabValue.measured(ioa, value, "GOOD"), offset + 5);
            }
            case Iec101LabTypes.C_IC_NA_1 -> {
                ensure(asdu, offset, 1);
                yield new Decoded(new Iec101LabValue(typeId, ioa, asdu[offset] & 0xFF, false, "GOOD"), offset + 1);
            }
            default -> throw new IOException("Unsupported IEC101-lab ASDU type: " + typeId);
        };
    }

    public static int asduTypeId(byte[] asdu) {
        return asdu.length == 0 ? -1 : asdu[0] & 0xFF;
    }

    public static int asduCommonAddress(byte[] asdu) {
        return little16(asdu, 4);
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

    private static void writeIoa(ByteArrayOutputStream out, int ioa) {
        out.write(ioa & 0xFF);
        out.write((ioa >>> 8) & 0xFF);
        out.write((ioa >>> 16) & 0xFF);
    }

    private static void writeFloat32(ByteArrayOutputStream out, float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(value);
        out.writeBytes(buffer.array());
    }

    private static float readFloat32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static int little16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int little24(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16);
    }

    private static void ensure(byte[] data, int offset, int need) throws IOException {
        if (offset + need > data.length) {
            throw new IOException("IEC101-lab ASDU truncated at offset " + offset);
        }
    }

    public enum ApduKind {
        I, S, U
    }

    public record ParsedApdu(ApduKind kind, int ctrl0, List<Iec101LabValue> values, byte[] raw) {
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

    private record Decoded(Iec101LabValue value, int next) {
    }
}
