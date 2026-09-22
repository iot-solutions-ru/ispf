package com.ispf.driver.opcuapubsub.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Lab UADP NetworkMessage header plus length-prefixed payload (not a DataSetMessage).
 * <p>
 * Header: version 1 in the low nibble, PublisherId flag set (bit 4), ExtendedFlags1 clear
 * so PublisherId is a single byte ({@code 0x11 0x01}). Then a 2-byte big-endian length and
 * lab payload bytes. Not a full OPC UA PubSub DataSetMessage or security stack.
 * <pre>
 *   uadpHeader(2) length(2 BE) labPayload...
 *   labPayload: msgType(1) keyLen(2) keyUTF8 valueType(1) valueLen(2) valueBytes
 * </pre>
 * Message types: GET=0x01, SAMPLE=0x02, PUBLISH=0x03, ACK=0x04.
 * Value types: NONE=0, FLOAT=1, DOUBLE=2, STRING=3.
 */
public final class OpcuaPubsubLabCodec {

    /** Handwritten UADP NetworkMessage header: version|PublisherIdFlag + publisherId 1. */
    private static final byte[] UADP_NETWORK_MESSAGE_HEADER = {(byte) 0x11, (byte) 0x01};

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
        int payloadLen = 1 + 2 + key.length + 1 + 2 + valueBytes.length;
        if (payloadLen > 0xFFFF) {
            throw new IllegalArgumentException("UADP-lab payload too long");
        }
        ByteBuffer buf = ByteBuffer.allocate(2 + 2 + payloadLen);
        buf.put(UADP_NETWORK_MESSAGE_HEADER);
        buf.putShort((short) payloadLen);
        buf.put(messageType);
        buf.putShort((short) key.length);
        buf.put(key);
        buf.put(valueType);
        buf.putShort((short) valueBytes.length);
        buf.put(valueBytes);
        return buf.array();
    }

    public static LabFrame decode(byte[] frame) {
        if (frame == null || frame.length < 7) {
            throw new IllegalArgumentException("UADP-lab frame too short: "
                    + (frame == null ? 0 : frame.length));
        }
        if (frame[0] != UADP_NETWORK_MESSAGE_HEADER[0]
                || frame[1] != UADP_NETWORK_MESSAGE_HEADER[1]) {
            throw new IllegalArgumentException("UADP-lab bad NetworkMessage header");
        }
        ByteBuffer buf = ByteBuffer.wrap(frame);
        buf.position(2);
        int payloadLen = buf.getShort() & 0xFFFF;
        if (buf.remaining() < payloadLen) {
            throw new IllegalArgumentException("UADP-lab truncated payload");
        }
        if (payloadLen < 6) {
            throw new IllegalArgumentException("UADP-lab payload too short");
        }
        byte messageType = buf.get();
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

    /**
     * Decoded lab payload carrier. Not a record — holds a {@code byte[]} value copy.
     */
    public static final class LabFrame {
        private final byte messageType;
        private final String key;
        private final byte valueType;
        private final byte[] value;

        public LabFrame(byte messageType, String key, byte valueType, byte[] value) {
            this.messageType = messageType;
            this.key = key;
            this.valueType = valueType;
            this.value = value == null ? new byte[0] : Arrays.copyOf(value, value.length);
        }

        public byte messageType() {
            return messageType;
        }

        public String key() {
            return key;
        }

        public byte valueType() {
            return valueType;
        }

        public byte[] value() {
            return Arrays.copyOf(value, value.length);
        }
    }
}
