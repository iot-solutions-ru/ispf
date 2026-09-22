package com.ispf.driver.dlms.codec;

import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPermanentException;
import com.ispf.driver.DriverUnsupportedOperationException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * IEC 62056-47 TCP WRAPPER framing plus Get-Request-Normal / Set-Request-Normal APDUs.
 * <p>
 * This codec does not implement an ACSE AARQ association stack. Reads use
 * Get-Request-Normal (and matching Get-Response-Normal) inside the TCP WRAPPER;
 * writes use Set-Request-Normal with the same Cosem-Attribute-Descriptor layout.
 */
public final class DlmsTcpWrapperCodec {

    public static final int VERSION = 1;
    public static final int CMD_GET_REQUEST = 0xC0;
    public static final int CMD_GET_RESPONSE = 0xC4;
    public static final int CMD_SET_REQUEST = 0xC1;
    public static final int CMD_SET_RESPONSE = 0xC5;
    public static final int CHOICE_NORMAL = 0x01;
    public static final int INVOKE_ID_AND_PRIORITY = 0x01;
    public static final int ACCESS_SELECTION_ABSENT = 0x00;
    public static final int GET_DATA_RESULT_DATA = 0x00;
    public static final int GET_DATA_RESULT_ERROR = 0x01;
    public static final int TAG_NULL = 0;
    public static final int TAG_BOOLEAN = 3;
    public static final int TAG_DOUBLE = 17;
    public static final int TAG_STRING = 10;
    public static final int TAG_OCTETS = 9;

    private DlmsTcpWrapperCodec() {
    }

    /**
     * Get-Request-Normal APDU: tag, choice, invoke-id-and-priority, Cosem-Attribute-Descriptor,
     * access-selection absent.
     */
    public static byte[] getRequest(DlmsObjectType objectType, String obis, int attributeIndex) throws DriverException {
        byte[] obisBytes = encodeObis(obis);
        ByteBuffer body = ByteBuffer.allocate(1 + 1 + 1 + 2 + 6 + 1 + 1).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) CMD_GET_REQUEST);
        body.put((byte) CHOICE_NORMAL);
        body.put((byte) INVOKE_ID_AND_PRIORITY);
        body.putShort((short) objectType.classId());
        body.put(obisBytes);
        body.put((byte) attributeIndex);
        body.put((byte) ACCESS_SELECTION_ABSENT);
        return body.array();
    }

    public static byte[] getResponse(int result, Object value) {
        if (result != 0) {
            return new byte[] {
                    (byte) CMD_GET_RESPONSE,
                    (byte) CHOICE_NORMAL,
                    (byte) INVOKE_ID_AND_PRIORITY,
                    (byte) GET_DATA_RESULT_ERROR,
                    (byte) result
            };
        }
        byte[] encoded = encodeValue(value);
        ByteBuffer body = ByteBuffer.allocate(4 + encoded.length).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) CMD_GET_RESPONSE);
        body.put((byte) CHOICE_NORMAL);
        body.put((byte) INVOKE_ID_AND_PRIORITY);
        body.put((byte) GET_DATA_RESULT_DATA);
        body.put(encoded);
        return body.array();
    }

    /**
     * Set-Request-Normal APDU with the same Cosem-Attribute-Descriptor layout as Get-Request-Normal,
     * then the encoded value.
     */
    public static byte[] setRequest(DlmsObjectType objectType, String obis, int attributeIndex, Object value)
            throws DriverException {
        byte[] obisBytes = encodeObis(obis);
        byte[] encoded = encodeValue(value);
        ByteBuffer body = ByteBuffer.allocate(1 + 1 + 1 + 2 + 6 + 1 + 1 + encoded.length).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) CMD_SET_REQUEST);
        body.put((byte) CHOICE_NORMAL);
        body.put((byte) INVOKE_ID_AND_PRIORITY);
        body.putShort((short) objectType.classId());
        body.put(obisBytes);
        body.put((byte) attributeIndex);
        body.put((byte) ACCESS_SELECTION_ABSENT);
        body.put(encoded);
        return body.array();
    }

    public static byte[] setResponse(int result) {
        return new byte[] {
                (byte) CMD_SET_RESPONSE,
                (byte) CHOICE_NORMAL,
                (byte) INVOKE_ID_AND_PRIORITY,
                (byte) result
        };
    }

    /** Builds an IEC 62056-47 TCP WRAPPER frame around an APDU. */
    public static byte[] wrapperFrame(int sourceWPort, int destinationWPort, byte[] apdu) {
        byte[] payload = apdu == null ? new byte[0] : apdu;
        ByteBuffer header = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.BIG_ENDIAN);
        header.putShort((short) VERSION);
        header.putShort((short) sourceWPort);
        header.putShort((short) destinationWPort);
        header.putShort((short) payload.length);
        header.put(payload);
        return header.array();
    }

    public static void writeFrame(OutputStream out, int sourceWPort, int destinationWPort, byte[] payload)
            throws IOException {
        out.write(wrapperFrame(sourceWPort, destinationWPort, payload));
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
        expectChoiceNormal(buffer);
        expectInvokeId(buffer);
        DlmsObjectType objectType = objectTypeForClass(Short.toUnsignedInt(buffer.getShort()));
        String obis = decodeObis(buffer);
        int attributeIndex = Byte.toUnsignedInt(buffer.get());
        expectAccessSelectionAbsent(buffer);
        return new GetRequest(objectType, obis, attributeIndex);
    }

    public static SetRequest parseSetRequest(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_SET_REQUEST);
        expectChoiceNormal(buffer);
        expectInvokeId(buffer);
        return new SetRequest(
                objectTypeForClass(Short.toUnsignedInt(buffer.getShort())),
                decodeObis(buffer),
                Byte.toUnsignedInt(buffer.get()),
                readAccessSelectionThenValue(buffer)
        );
    }

    public static Object parseGetResponse(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_GET_RESPONSE);
        expectChoiceNormal(buffer);
        expectInvokeId(buffer);
        int dataResult = Byte.toUnsignedInt(buffer.get());
        if (dataResult == GET_DATA_RESULT_ERROR) {
            int result = Byte.toUnsignedInt(buffer.get());
            throw new DriverPermanentException("DLMS GET rejected with result " + result);
        }
        if (dataResult != GET_DATA_RESULT_DATA) {
            throw new DriverPermanentException("Unexpected DLMS Get-Data-Result 0x"
                    + Integer.toHexString(dataResult).toUpperCase(Locale.ROOT));
        }
        return decodeValue(buffer);
    }

    public static void parseSetResponse(byte[] payload) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        expectCommand(buffer, CMD_SET_RESPONSE);
        expectChoiceNormal(buffer);
        expectInvokeId(buffer);
        int result = Byte.toUnsignedInt(buffer.get());
        if (result != 0) {
            throw new DriverPermanentException("DLMS SET rejected with result " + result);
        }
    }

    public static byte[] encodeObis(String obis) throws DriverException {
        String[] parts = obis.split("\\.");
        if (parts.length != 6) {
            throw new DriverConfigurationException("Invalid OBIS code: " + obis);
        }
        byte[] result = new byte[6];
        for (int i = 0; i < parts.length; i++) {
            int value = Integer.parseInt(parts[i]);
            if (value < 0 || value > 255) {
                throw new DriverConfigurationException("Invalid OBIS component: " + obis);
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
            default -> throw new DriverUnsupportedOperationException("Unsupported DLMS data tag " + tag);
        };
    }

    private static Object readAccessSelectionThenValue(ByteBuffer buffer) throws DriverException {
        expectAccessSelectionAbsent(buffer);
        return decodeValue(buffer);
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
            throw new DriverPermanentException("Unexpected DLMS command 0x"
                    + Integer.toHexString(actual).toUpperCase(Locale.ROOT));
        }
    }

    private static void expectChoiceNormal(ByteBuffer buffer) throws DriverException {
        int choice = Byte.toUnsignedInt(buffer.get());
        if (choice != CHOICE_NORMAL) {
            throw new DriverPermanentException("Unexpected DLMS request choice 0x"
                    + Integer.toHexString(choice).toUpperCase(Locale.ROOT));
        }
    }

    private static void expectInvokeId(ByteBuffer buffer) throws DriverException {
        int invokeId = Byte.toUnsignedInt(buffer.get());
        if (invokeId != INVOKE_ID_AND_PRIORITY) {
            throw new DriverPermanentException("Unexpected DLMS invoke-id-and-priority 0x"
                    + Integer.toHexString(invokeId).toUpperCase(Locale.ROOT));
        }
    }

    private static void expectAccessSelectionAbsent(ByteBuffer buffer) throws DriverException {
        int selection = Byte.toUnsignedInt(buffer.get());
        if (selection != ACCESS_SELECTION_ABSENT) {
            throw new DriverUnsupportedOperationException("DLMS access-selection is not supported");
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

    public static final class Frame {
        private final int sourceWPort;
        private final int destinationWPort;
        private final byte[] payload;

        Frame(int sourceWPort, int destinationWPort, byte[] payload) {
            this.sourceWPort = sourceWPort;
            this.destinationWPort = destinationWPort;
            this.payload = payload == null ? new byte[0] : payload.clone();
        }

        public int sourceWPort() {
            return sourceWPort;
        }

        public int destinationWPort() {
            return destinationWPort;
        }

        public byte[] payload() {
            return payload.clone();
        }
    }

    public record GetRequest(DlmsObjectType objectType, String obis, int attributeIndex) {
    }

    public record SetRequest(DlmsObjectType objectType, String obis, int attributeIndex, Object value) {
    }
}
