package com.ispf.driver.iec104.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

final class Iec104Codec {

    private Iec104Codec() {
    }

    static byte[] encodeAsdu(Iec104Asdu asdu) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(asdu.typeId());
        out.write(asdu.values().size() & 0xff);
        writeLittle16(out, asdu.cause() | ((asdu.originatorAddress() & 0xff) << 8));
        writeLittle16(out, asdu.commonAddress());
        for (Iec104Value value : asdu.values()) {
            writeIoa(out, value.ioa());
            writeValue(out, asdu.typeId(), value);
        }
        return out.toByteArray();
    }

    static Iec104Asdu decodeAsdu(byte[] payload) throws IOException {
        if (payload.length < 6) {
            throw new IOException("IEC104 ASDU too short: " + payload.length);
        }
        int typeId = unsigned(payload[0]);
        int vsq = unsigned(payload[1]);
        int causeField = little16(payload, 2);
        int commonAddress = little16(payload, 4);
        int count = vsq & 0x7f;
        boolean sequence = (vsq & 0x80) != 0;
        int offset = 6;
        List<Iec104Value> values = new ArrayList<>();
        int baseIoa = -1;
        for (int index = 0; index < count; index++) {
            int ioa;
            if (sequence && index > 0) {
                ioa = baseIoa + index;
            } else {
                ensureRemaining(payload, offset, 3, "IOA");
                ioa = little24(payload, offset);
                offset += 3;
                if (index == 0) {
                    baseIoa = ioa;
                }
            }
            DecodedValue decoded = readValue(payload, offset, typeId, ioa);
            values.add(decoded.value());
            offset = decoded.nextOffset();
        }
        return new Iec104Asdu(typeId, causeField & 0x3f, (causeField >>> 8) & 0xff, commonAddress, values);
    }

    private static void writeValue(ByteArrayOutputStream out, int typeId, Iec104Value value) throws IOException {
        switch (typeId) {
            case Iec104Type.M_SP_NA_1 -> out.write((value.booleanValue() ? 1 : 0) | encodeQuality(value.quality()));
            case Iec104Type.M_DP_NA_1 -> out.write((value.numericValue() == 0.0 ? 1 : (int) value.numericValue()) & 0x03);
            case Iec104Type.M_ME_NA_1, Iec104Type.C_SE_NA_1 -> {
                writeLittle16(out, encodeNormalized(value.numericValue()));
                out.write(typeId == Iec104Type.C_SE_NA_1 ? 0 : encodeQuality(value.quality()));
            }
            case Iec104Type.M_ME_NC_1, Iec104Type.M_ME_TF_1, Iec104Type.C_SE_NC_1 -> {
                writeFloat32(out, (float) value.numericValue());
                out.write(typeId == Iec104Type.C_SE_NC_1 ? 0 : encodeQuality(value.quality()));
                if (typeId == Iec104Type.M_ME_TF_1) {
                    out.write(new byte[7]);
                }
            }
            case Iec104Type.C_SC_NA_1 -> out.write(value.booleanValue() ? 1 : 0);
            case Iec104Type.C_IC_NA_1 -> out.write((int) value.numericValue() & 0xff);
            case Iec104Type.C_RD_NA_1 -> {
                // Read command has only the IOA.
            }
            default -> throw new IOException("Unsupported IEC104 ASDU type for encode: " + typeId);
        }
    }

    private static DecodedValue readValue(byte[] payload, int offset, int typeId, int ioa) throws IOException {
        return switch (typeId) {
            case Iec104Type.M_SP_NA_1 -> {
                ensureRemaining(payload, offset, 1, "single point");
                int raw = unsigned(payload[offset]);
                yield new DecodedValue(Iec104Value.singlePoint(ioa, (raw & 0x01) != 0, decodeQuality(raw)), offset + 1);
            }
            case Iec104Type.M_DP_NA_1 -> {
                ensureRemaining(payload, offset, 1, "double point");
                int raw = unsigned(payload[offset]);
                yield new DecodedValue(Iec104Value.doublePoint(ioa, raw & 0x03, decodeQuality(raw)), offset + 1);
            }
            case Iec104Type.M_ME_NA_1 -> {
                ensureRemaining(payload, offset, 3, "normalized value");
                double value = (short) little16(payload, offset) / 32768.0;
                yield new DecodedValue(Iec104Value.normalized(ioa, value, decodeQuality(unsigned(payload[offset + 2]))), offset + 3);
            }
            case Iec104Type.M_ME_NC_1 -> {
                ensureRemaining(payload, offset, 5, "short float");
                double value = readFloat32(payload, offset);
                yield new DecodedValue(Iec104Value.shortFloat(ioa, value, decodeQuality(unsigned(payload[offset + 4]))), offset + 5);
            }
            case Iec104Type.M_ME_TF_1 -> {
                ensureRemaining(payload, offset, 12, "short float with timestamp");
                double value = readFloat32(payload, offset);
                yield new DecodedValue(new Iec104Value(ioa, Iec104Type.M_ME_TF_1, value,
                        decodeQuality(unsigned(payload[offset + 4]))), offset + 12);
            }
            case Iec104Type.C_SC_NA_1 -> {
                ensureRemaining(payload, offset, 1, "single command");
                yield new DecodedValue(new Iec104Value(ioa, typeId, (payload[offset] & 0x01) != 0, "GOOD"), offset + 1);
            }
            case Iec104Type.C_SE_NA_1 -> {
                ensureRemaining(payload, offset, 3, "normalized setpoint");
                double value = (short) little16(payload, offset) / 32768.0;
                yield new DecodedValue(new Iec104Value(ioa, typeId, value, "GOOD"), offset + 3);
            }
            case Iec104Type.C_SE_NC_1 -> {
                ensureRemaining(payload, offset, 5, "float setpoint");
                yield new DecodedValue(new Iec104Value(ioa, typeId, (double) readFloat32(payload, offset), "GOOD"), offset + 5);
            }
            case Iec104Type.C_IC_NA_1 -> {
                ensureRemaining(payload, offset, 1, "interrogation qualifier");
                yield new DecodedValue(new Iec104Value(ioa, typeId, (double) unsigned(payload[offset]), "GOOD"), offset + 1);
            }
            case Iec104Type.C_RD_NA_1 -> new DecodedValue(new Iec104Value(ioa, typeId, 0.0, "GOOD"), offset);
            default -> throw new IOException("Unsupported IEC104 ASDU type for decode: " + typeId);
        };
    }

    private static int encodeNormalized(double value) {
        double clamped = Math.max(-1.0, Math.min(0.999969482421875, value));
        return ((short) Math.round(clamped * 32768.0)) & 0xffff;
    }

    private static int encodeQuality(String quality) {
        if ("INVALID".equals(quality)) {
            return 0x80;
        }
        if ("NOT_TOPICAL".equals(quality)) {
            return 0x40;
        }
        if ("SUBSTITUTED".equals(quality)) {
            return 0x20;
        }
        if ("BLOCKED".equals(quality)) {
            return 0x10;
        }
        return 0;
    }

    private static String decodeQuality(int raw) {
        if ((raw & 0x80) != 0) {
            return "INVALID";
        }
        if ((raw & 0x40) != 0) {
            return "NOT_TOPICAL";
        }
        if ((raw & 0x20) != 0) {
            return "SUBSTITUTED";
        }
        if ((raw & 0x10) != 0) {
            return "BLOCKED";
        }
        return "GOOD";
    }

    private static void writeLittle16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeIoa(ByteArrayOutputStream out, int ioa) {
        out.write(ioa & 0xff);
        out.write((ioa >>> 8) & 0xff);
        out.write((ioa >>> 16) & 0xff);
    }

    private static int little16(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8);
    }

    private static int little24(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8) | (unsigned(bytes[offset + 2]) << 16);
    }

    private static void writeFloat32(ByteArrayOutputStream out, float value) throws IOException {
        out.write(ByteBuffer.allocate(Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(value)
                .array());
    }

    private static float readFloat32(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static void ensureRemaining(byte[] payload, int offset, int needed, String field) throws IOException {
        if (payload.length - offset < needed) {
            throw new IOException("IEC104 ASDU truncated while reading " + field);
        }
    }

    private record DecodedValue(Iec104Value value, int nextOffset) {
    }
}
