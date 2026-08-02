package com.ispf.driver.dlms.codec;

import com.ispf.driver.DriverException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Clean-room TCP WRAPPER framing plus a compact xDLMS subset used by this driver.
 * <p>
 * The APDUs intentionally cover only no-security association, logical-name GET,
 * and logical-name SET for Data/Register-style values.
 */
public final class DlmsTcpWrapperCodec {

    public static final int VERSION = 1;
    public static final int CMD_ASSOCIATE_REQUEST = 0x60;
    public static final int CMD_ASSOCIATE_RESPONSE = 0x61;
    public static final int CMD_GET_REQUEST = 0xC0;
    public static final int CMD_GET_RESPONSE = 0xC4;
    public static final int CMD_SET_REQUEST = 0xC1;
    public static final int CMD_SET_RESPONSE = 0xC5;
    public static final int TAG_NULL = 0;
    public static final int TAG_BOOLEAN = 3;
    public static final int TAG_DOUBLE = 17;
    public static final int TAG_STRING = 10;
    public static final int TAG_OCTETS = 9;

    private DlmsTcpWrapperCodec() {
    }

    public static byte[] associateRequest(int clientAddress, int logicalDevice) {
        ByteBuffer body = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) CMD_ASSOCIATE_REQUEST);
        body.putShort((short) clientAddress);
        body.putShort((short) logicalDevice);
        return body.array();
    }

    public static byte[] associateResponse(boolean accepted) {
        return new byte[] {(byte) CMD_ASSOCIATE_RESPONSE, (byte) (accepted ? 0 : 1)};
    }

    public static byte[] getRequest(DlmsObjectType objectType, String obis, int attributeIndex) throws DriverException {
        byte[] obisBytes = encodeObis(obis);
        ByteBuffer body = ByteBuffer.allocate(1 + 2 + 6 + 1).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) CMD_GET_REQUEST);
        body.putShort((short) objectType.classId());
        body.put(obisBytes);
        body.put((byte) attributeIndex);
        return body.array();
    }

    public static byte[] getResponse(int result, Object value) {
        byte[] encoded = encodeValue(value);
        ByteBuffer body = ByteBuffer.allocate(2 + encoded.length).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) CMD_GET_RESPONSE);
        body.put((byte) result);
        body.put(encoded);
        return body.array();
    }

    public static byte[] setRequest(DlmsObjectType objectType, String obis, int attributeIndex, Object value)
            throws DriverException {
        byte[] obisBytes = encodeObis(obis);
        byte[] encoded = encodeValue(value);
        ByteBuffer body = ByteBuffer.allocate(1 + 2 + 6 + 1 + encoded.length).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) CMD_SET_REQUEST);
        body.putShort((short) objectType.classId());
        body.put(obisBytes);
        body.put((byte) attributeIndex);
        body.put(encoded);
        return body.array();
    }

    public static byte[] setResponse(int result) {
        return new byte[] {(byte) CMD_SET_RESPONSE, (byte) result};
    }

    public static void writeFrame(OutputStream out, int sourceWPort, int destinationWPort, byte[] payload)
            throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        header.putShort((short) VERSION);
        header.putShort((short) sourceWPort);
        header.putShort((short) destinationWPort);
        header.putShort((short) payload.length);
        out.write(header.array());
        out.write(payload);
        out.flush();
    }

    public static Frame readFrame(InputStream in) throws IOException {
        byte[] header = readFully(in, 8);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        int version = Short.toUnsignedInt(buffer.getShort());
        if (version != VERSION) {
            throw new IOException("Unsupported DLMS wrapper version " + version);
        }
        int source = Short.toUnsignedInt(buffer.getShort());
        int destination = Short.toUnsignedInt(buffer.getShort());
        int length = Short.toUnsignedInt(buffer.getShort());
        return new Frame(source, destination, readFully(in, length));
    }

    public static GetRequest parseGetRequest(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_GET_REQUEST);
        return new GetRequest(objectTypeForClass(Short.toUnsignedInt(buffer.getShort())), decodeObis(buffer), Byte.toUnsignedInt(buffer.get()));
    }

    public static SetRequest parseSetRequest(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_SET_REQUEST);
        return new SetRequest(
                objectTypeForClass(Short.toUnsignedInt(buffer.getShort())),
                decodeObis(buffer),
                Byte.toUnsignedInt(buffer.get()),
                decodeValue(buffer)
        );
    }

    public static Object parseGetResponse(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_GET_RESPONSE);
        int result = Byte.toUnsignedInt(buffer.get());
        if (result != 0) {
            throw new DriverException("DLMS GET rejected with result " + result);
        }
        return decodeValue(buffer);
    }

    public static void parseSetResponse(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_SET_RESPONSE);
        int result = Byte.toUnsignedInt(buffer.get());
        if (result != 0) {
            throw new DriverException("DLMS SET rejected with result " + result);
        }
    }

    public static boolean parseAssociateResponse(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_ASSOCIATE_RESPONSE);
        return Byte.toUnsignedInt(buffer.get()) == 0;
    }

    public static byte[] encodeObis(String obis) throws DriverException {
        String[] parts = obis.split("\\.");
        if (parts.length != 6) {
            throw new DriverException("Invalid OBIS code: " + obis);
        }
        byte[] result = new byte[6];
        for (int i = 0; i < parts.length; i++) {
            int value = Integer.parseInt(parts[i]);
            if (value < 0 || value > 255) {
                throw new DriverException("Invalid OBIS component: " + obis);
            }
            result[i] = (byte) value;
        }
        return result;
    }

    private static String decodeObis(ByteBuffer buffer) {
        int[] parts = new int[6];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = Byte.toUnsignedInt(buffer.get());
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3] + "." + parts[4] + "." + parts[5];
    }

    private static byte[] encodeValue(Object value) {
        if (value == null) {
            return new byte[] {(byte) TAG_NULL};
        }
        if (value instanceof Boolean bool) {
            return new byte[] {(byte) TAG_BOOLEAN, (byte) (bool ? 1 : 0)};
        }
        if (value instanceof Number number) {
            ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
            buffer.put((byte) TAG_DOUBLE);
            buffer.putDouble(number.doubleValue());
            return buffer.array();
        }
        byte[] bytes = value instanceof byte[] array
                ? array
                : String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(3 + bytes.length).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) (value instanceof byte[] ? TAG_OCTETS : TAG_STRING));
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
        return buffer.array();
    }

    private static Object decodeValue(ByteBuffer buffer) throws DriverException {
        int tag = Byte.toUnsignedInt(buffer.get());
        return switch (tag) {
            case TAG_NULL -> null;
            case TAG_BOOLEAN -> buffer.get() != 0;
            case TAG_DOUBLE -> buffer.getDouble();
            case TAG_STRING -> {
                byte[] bytes = sizedBytes(buffer);
                yield new String(bytes, StandardCharsets.UTF_8);
            }
            case TAG_OCTETS -> sizedBytes(buffer);
            default -> throw new DriverException("Unsupported DLMS data tag " + tag);
        };
    }

    private static byte[] sizedBytes(ByteBuffer buffer) {
        int length = Short.toUnsignedInt(buffer.getShort());
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    private static void expectCommand(ByteBuffer buffer, int expected) throws DriverException {
        int actual = Byte.toUnsignedInt(buffer.get());
        if (actual != expected) {
            throw new DriverException("Unexpected DLMS command 0x" + Integer.toHexString(actual));
        }
    }

    private static DlmsObjectType objectTypeForClass(int classId) throws DriverException {
        return Arrays.stream(DlmsObjectType.values())
                .filter(type -> type.classId() == classId)
                .findFirst()
                .orElseThrow(() -> new DriverException("Unsupported COSEM class id " + classId));
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = in.read(data, offset, length - offset);
            if (count < 0) {
                throw new EOFException("Unexpected end of DLMS wrapper stream");
            }
            offset += count;
        }
        return data;
    }

    public record Frame(int sourceWPort, int destinationWPort, byte[] payload) {
    }

    public record GetRequest(DlmsObjectType objectType, String obis, int attributeIndex) {
    }

    public record SetRequest(DlmsObjectType objectType, String obis, int attributeIndex, Object value) {
    }
}
