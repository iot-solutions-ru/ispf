package com.ispf.driver.opcuapubsub.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Minimal UADP-like UDP lab codec (fixed header + dataset payload).
 * <p>
 * Not full OPC UA PubSub / MQTT / broker / security. Lab ≠ field.
 * <pre>
 *   magic "UADP"(4) version(1) msgType(1) reserved(2)
 *   keyLen(2) keyUTF8  valueType(1) valueLen(2) valueBytes
 * </pre>
 * Message types: GET=0x01, SAMPLE=0x02, PUBLISH=0x03, ACK=0x04.
 * Value types: NONE=0, FLOAT=1, DOUBLE=2, STRING=3.
 */
public final class OpcuaPubsubLabCodec {

    public static final byte[] MAGIC = {'U', 'A', 'D', 'P'};
    public static final byte VERSION = 0x01;
    public static final byte MSG_GET = 0x01;
    public static final byte MSG_SAMPLE = 0x02;
    public static final byte MSG_PUBLISH = 0x03;
    public static final byte MSG_ACK = 0x04;

    public static final byte TYPE_NONE = 0;
    public static final byte TYPE_FLOAT = 1;
    public static final byte TYPE_DOUBLE = 2;
    public static final byte TYPE_STRING = 3;

    private OpcuaPubsubLabCodec() {
    }

    public static byte[] encodeGet(String wireToken) {
        return encode(MSG_GET, wireToken, TYPE_NONE, new byte[0]);
    }

    public static byte[] encodeSample(String wireToken, double value) {
        return encode(MSG_SAMPLE, wireToken, TYPE_DOUBLE, doubleBytes(value));
    }

    public static byte[] encodeSampleFloat(String wireToken, float value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putFloat(value);
        return encode(MSG_SAMPLE, wireToken, TYPE_FLOAT, buf.array());
    }

    public static byte[] encodeSampleString(String wireToken, String text) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        return encode(MSG_SAMPLE, wireToken, TYPE_STRING, utf8);
    }

    public static byte[] encodePublish(String wireToken, double value) {
        return encode(MSG_PUBLISH, wireToken, TYPE_DOUBLE, doubleBytes(value));
    }

    public static byte[] encodeAck(String wireToken) {
        return encode(MSG_ACK, wireToken, TYPE_NONE, new byte[0]);
    }

    public static byte[] encode(byte messageType, String wireToken, byte valueType, byte[] valueBytes) {
        byte[] key = wireToken.getBytes(StandardCharsets.UTF_8);
        if (key.length > 0xFFFF) {
            throw new IllegalArgumentException("UADP-lab key too long");
        }
        if (valueBytes.length > 0xFFFF) {
            throw new IllegalArgumentException("UADP-lab value too long");
        }
        ByteBuffer buf = ByteBuffer.allocate(8 + 2 + key.length + 1 + 2 + valueBytes.length);
        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put(messageType);
        buf.putShort((short) 0);
        buf.putShort((short) key.length);
        buf.put(key);
        buf.put(valueType);
        buf.putShort((short) valueBytes.length);
        buf.put(valueBytes);
        return buf.array();
    }

    public static LabFrame decode(byte[] frame) {
        if (frame == null || frame.length < 13) {
            throw new IllegalArgumentException("UADP-lab frame too short: "
                    + (frame == null ? 0 : frame.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(frame);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("UADP-lab bad magic");
        }
        byte version = buf.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("UADP-lab unexpected version: " + version);
        }
        byte messageType = buf.get();
        buf.getShort(); // reserved
        int keyLen = buf.getShort() & 0xFFFF;
        if (buf.remaining() < keyLen + 3) {
            throw new IllegalArgumentException("UADP-lab truncated key");
        }
        byte[] keyBytes = new byte[keyLen];
        buf.get(keyBytes);
        String key = new String(keyBytes, StandardCharsets.UTF_8);
        byte valueType = buf.get();
        int valueLen = buf.getShort() & 0xFFFF;
        if (buf.remaining() < valueLen) {
            throw new IllegalArgumentException("UADP-lab truncated value");
        }
        byte[] valueBytes = new byte[valueLen];
        buf.get(valueBytes);
        return new LabFrame(messageType, key, valueType, valueBytes);
    }

    public static double decodeNumeric(LabFrame frame) {
        return switch (frame.valueType()) {
            case TYPE_FLOAT -> {
                if (frame.value().length < 4) {
                    throw new IllegalArgumentException("UADP-lab float truncated");
                }
                yield ByteBuffer.wrap(frame.value()).getFloat();
            }
            case TYPE_DOUBLE -> {
                if (frame.value().length < 8) {
                    throw new IllegalArgumentException("UADP-lab double truncated");
                }
                yield ByteBuffer.wrap(frame.value()).getDouble();
            }
            case TYPE_STRING -> {
                String text = new String(frame.value(), StandardCharsets.UTF_8).trim();
                yield Double.parseDouble(text);
            }
            case TYPE_NONE -> 0.0;
            default -> throw new IllegalArgumentException(
                    "UADP-lab unsupported value type: " + (frame.valueType() & 0xFF));
        };
    }

    public static String valueTypeName(byte valueType) {
        return switch (valueType) {
            case TYPE_NONE -> "none";
            case TYPE_FLOAT -> "float";
            case TYPE_DOUBLE -> "double";
            case TYPE_STRING -> "string";
            default -> "0x" + Integer.toHexString(valueType & 0xFF).toUpperCase(Locale.ROOT);
        };
    }

    private static byte[] doubleBytes(double value) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putDouble(value);
        return buf.array();
    }

    public record LabFrame(byte messageType, String key, byte valueType, byte[] value) {
    }
}
