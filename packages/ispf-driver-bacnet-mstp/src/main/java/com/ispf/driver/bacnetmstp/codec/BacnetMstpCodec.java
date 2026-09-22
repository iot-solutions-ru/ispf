package com.ispf.driver.bacnetmstp.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * BACnet MS/TP (ASHRAE 135 clause 9) framing over a TCP serial-server transport.
 * <p>
 * Preamble {@code 55 FF}, then type/dest/src/length, header CRC-8, optional data and
 * data CRC-16 (little-endian). NPDU + confirmed APDU payloads ride in the data field.
 * Not a native RS-485 token-passing master. Clean-room Apache-2.0, JDK only.
 */
public final class BacnetMstpCodec {

    public static final int FRAME_TYPE_TOKEN = 0;
    public static final int FRAME_TYPE_BACNET_DATA_EXPECTING_REPLY = 5;
    public static final int SERVICE_READ_PROPERTY = 12;
    public static final int SERVICE_WRITE_PROPERTY = 15;
    public static final int PRESENT_VALUE = 85;

    private static final int NPDU_VERSION = 0x01;
    private static final int NPDU_EXPECTING_REPLY = 0x04;
    private static final int PDU_CONFIRMED_REQUEST = 0x00;
    private static final int PDU_SIMPLE_ACK = 0x20;
    private static final int PDU_COMPLEX_ACK = 0x30;
    private static final int MAX_APDU = 0x05;
    private static final int PREAMBLE_1 = 0x55;
    private static final int PREAMBLE_2 = 0xFF;
    private static final int DATA_CRC_POLY = 0x8408;

    private BacnetMstpCodec() {
    }

    /**
     * Header CRC-8 (Annex G / poly {@code 0x119} form): init {@code 0xFF},
     * ones-complement of the remainder over type, dest, src, length high, length low.
     */
    public static int headerCrc(byte[] headerFields) {
        int crc = 0xFF;
        for (byte value : headerFields) {
            crc = headerCrcAccumulate(crc, value & 0xFF);
        }
        return ~crc & 0xFF;
    }

    /** Data CRC-16/BACnet: poly {@code 0x8408}, init {@code 0xFFFF}, ones-complemented. */
    public static int dataCrc(byte[] data) {
        int crc = 0xFFFF;
        for (byte value : data) {
            crc ^= value & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ DATA_CRC_POLY;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return ~crc & 0xFFFF;
    }

    public static byte[] encodeReadProperty(
            int destination,
            int source,
            int invokeId,
            int encodedObjectId,
            int propertyId
    ) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_CONFIRMED_REQUEST);
        apdu.write(MAX_APDU);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_READ_PROPERTY);
        writeContextObjectId(apdu, 0, encodedObjectId);
        writeContextUnsigned(apdu, 1, propertyId);
        return wrapMstp(FRAME_TYPE_BACNET_DATA_EXPECTING_REPLY, destination, source, apdu.toByteArray(), true);
    }

    public static byte[] encodeReadPropertyAck(
            int destination,
            int source,
            int invokeId,
            int encodedObjectId,
            int propertyId,
            float value
    ) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_COMPLEX_ACK);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_READ_PROPERTY);
        writeContextObjectId(apdu, 0, encodedObjectId);
        writeContextUnsigned(apdu, 1, propertyId);
        apdu.write(0x3E);
        writeApplicationReal(apdu, value);
        apdu.write(0x3F);
        return wrapMstp(FRAME_TYPE_BACNET_DATA_EXPECTING_REPLY, destination, source, apdu.toByteArray(), false);
    }

    public static byte[] encodeWriteProperty(
            int destination,
            int source,
            int invokeId,
            int encodedObjectId,
            int propertyId,
            float value
    ) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_CONFIRMED_REQUEST);
        apdu.write(MAX_APDU);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_WRITE_PROPERTY);
        writeContextObjectId(apdu, 0, encodedObjectId);
        writeContextUnsigned(apdu, 1, propertyId);
        apdu.write(0x3E);
        writeApplicationReal(apdu, value);
        apdu.write(0x3F);
        return wrapMstp(FRAME_TYPE_BACNET_DATA_EXPECTING_REPLY, destination, source, apdu.toByteArray(), true);
    }

    public static byte[] encodeSimpleAck(int destination, int source, int invokeId, int serviceChoice) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_SIMPLE_ACK);
        apdu.write(invokeId & 0xFF);
        apdu.write(serviceChoice & 0xFF);
        return wrapMstp(FRAME_TYPE_BACNET_DATA_EXPECTING_REPLY, destination, source, apdu.toByteArray(), false);
    }

    public static byte[] wrapMstp(int frameType, int destination, int source, byte[] apdu, boolean expectingReply) {
        byte[] npduApdu = wrapNpdu(apdu, expectingReply);
        int length = npduApdu.length;
        byte[] frame = new byte[8 + length + (length > 0 ? 2 : 0)];
        frame[0] = (byte) PREAMBLE_1;
        frame[1] = (byte) PREAMBLE_2;
        frame[2] = (byte) (frameType & 0xFF);
        frame[3] = (byte) (destination & 0xFF);
        frame[4] = (byte) (source & 0xFF);
        frame[5] = (byte) ((length >>> 8) & 0xFF);
        frame[6] = (byte) (length & 0xFF);
        byte[] headerFields = new byte[] { frame[2], frame[3], frame[4], frame[5], frame[6] };
        frame[7] = (byte) headerCrc(headerFields);
        if (length > 0) {
            System.arraycopy(npduApdu, 0, frame, 8, length);
            int crc = dataCrc(npduApdu);
            frame[8 + length] = (byte) (crc & 0xFF);
            frame[8 + length + 1] = (byte) ((crc >>> 8) & 0xFF);
        }
        return frame;
    }

    public static Message decode(byte[] frame) {
        if (frame == null || frame.length < 8) {
            throw new IllegalArgumentException("BACnet MS/TP frame too short");
        }
        if ((frame[0] & 0xFF) != PREAMBLE_1 || (frame[1] & 0xFF) != PREAMBLE_2) {
            throw new IllegalArgumentException("BACnet MS/TP preamble missing");
        }
        int frameType = frame[2] & 0xFF;
        int destination = frame[3] & 0xFF;
        int source = frame[4] & 0xFF;
        int length = ((frame[5] & 0xFF) << 8) | (frame[6] & 0xFF);
        byte[] headerFields = new byte[] { frame[2], frame[3], frame[4], frame[5], frame[6] };
        int expectedHeaderCrc = headerCrc(headerFields);
        if ((frame[7] & 0xFF) != expectedHeaderCrc) {
            throw new IllegalArgumentException("BACnet MS/TP header CRC mismatch");
        }
        if (frameType == FRAME_TYPE_TOKEN) {
            return new TokenFrame(destination, source);
        }
        if (length == 0) {
            throw new IllegalArgumentException("BACnet MS/TP data frame has empty payload");
        }
        if (frame.length < 8 + length + 2) {
            throw new IllegalArgumentException("Incomplete BACnet MS/TP frame");
        }
        byte[] data = new byte[length];
        System.arraycopy(frame, 8, data, 0, length);
        int receivedDataCrc = (frame[8 + length] & 0xFF) | ((frame[8 + length + 1] & 0xFF) << 8);
        if (receivedDataCrc != dataCrc(data)) {
            throw new IllegalArgumentException("BACnet MS/TP data CRC mismatch");
        }
        return decodeNpduApdu(data, destination, source);
    }

    public static byte[] readFrame(InputStream in) throws IOException {
        while (true) {
            int b0 = in.read();
            if (b0 < 0) {
                throw new EOFException("EOF seeking BACnet MS/TP preamble");
            }
            if (b0 != PREAMBLE_1) {
                continue;
            }
            int b1 = in.read();
            if (b1 < 0) {
                throw new EOFException("EOF seeking BACnet MS/TP preamble");
            }
            if (b1 != PREAMBLE_2) {
                continue;
            }
            byte[] header = readFully(in, 6);
            int length = ((header[3] & 0xFF) << 8) | (header[4] & 0xFF);
            int total = 8 + length + (length > 0 ? 2 : 0);
            byte[] frame = new byte[total];
            frame[0] = (byte) PREAMBLE_1;
            frame[1] = (byte) PREAMBLE_2;
            System.arraycopy(header, 0, frame, 2, 6);
            if (length > 0) {
                byte[] rest = readFully(in, length + 2);
                System.arraycopy(rest, 0, frame, 8, rest.length);
            }
            return frame;
        }
    }

    private static Message decodeNpduApdu(byte[] data, int destination, int source) {
        if (data.length < 4) {
            throw new IllegalArgumentException("BACnet NPDU/APDU too short");
        }
        if ((data[0] & 0xFF) != NPDU_VERSION) {
            throw new IllegalArgumentException("Unsupported NPDU version");
        }
        int apduOffset = 2;
        int pduType = data[apduOffset] & 0xF0;
        if (pduType == PDU_CONFIRMED_REQUEST) {
            int invokeId = data[apduOffset + 2] & 0xFF;
            int service = data[apduOffset + 3] & 0xFF;
            Cursor cursor = new Cursor(apduOffset + 4, data.length);
            int objectId = readContextObjectId(data, cursor, 0);
            int property = readContextUnsigned(data, cursor, 1);
            if (service == SERVICE_READ_PROPERTY) {
                return new ReadPropertyRequest(invokeId, objectId, property, destination, source);
            }
            if (service == SERVICE_WRITE_PROPERTY) {
                expectByte(data, cursor, 0x3E);
                float value = readApplicationReal(data, cursor);
                expectByte(data, cursor, 0x3F);
                return new WritePropertyRequest(invokeId, objectId, property, value, destination, source);
            }
        }
        if (pduType == PDU_COMPLEX_ACK) {
            int invokeId = data[apduOffset + 1] & 0xFF;
            int service = data[apduOffset + 2] & 0xFF;
            if (service == SERVICE_READ_PROPERTY) {
                Cursor cursor = new Cursor(apduOffset + 3, data.length);
                int objectId = readContextObjectId(data, cursor, 0);
                int property = readContextUnsigned(data, cursor, 1);
                expectByte(data, cursor, 0x3E);
                float value = readApplicationReal(data, cursor);
                expectByte(data, cursor, 0x3F);
                return new ReadPropertyAck(invokeId, objectId, property, value, destination, source);
            }
        }
        if (pduType == PDU_SIMPLE_ACK) {
            return new SimpleAck(data[apduOffset + 1] & 0xFF, data[apduOffset + 2] & 0xFF, destination, source);
        }
        throw new IllegalArgumentException("Unsupported BACnet MS/TP APDU");
    }

    private static byte[] wrapNpdu(byte[] apdu, boolean expectingReply) {
        ByteBuffer buffer = ByteBuffer.allocate(2 + apdu.length);
        buffer.put((byte) NPDU_VERSION);
        buffer.put((byte) (expectingReply ? NPDU_EXPECTING_REPLY : 0));
        buffer.put(apdu);
        return buffer.array();
    }

    private static int headerCrcAccumulate(int crc, int data) {
        int crc16 = (crc ^ data) & 0xFF;
        crc16 = crc16 ^ (crc16 << 1) ^ (crc16 << 2) ^ (crc16 << 3)
                ^ (crc16 << 4) ^ (crc16 << 5) ^ (crc16 << 6) ^ (crc16 << 7);
        return (crc16 & 0xFE) ^ ((crc16 >>> 8) & 0x01);
    }

    private static void writeContextObjectId(ByteArrayOutputStream out, int tag, int encodedObjectId) {
        out.write((tag << 4) | 0x0C);
        writeInt(out, encodedObjectId);
    }

    private static void writeContextUnsigned(ByteArrayOutputStream out, int tag, int value) {
        byte[] encoded = unsignedBytes(value);
        out.write((tag << 4) | 0x08 | encoded.length);
        out.writeBytes(encoded);
    }

    private static void writeApplicationReal(ByteArrayOutputStream out, float value) {
        out.write(0x44);
        writeInt(out, Float.floatToIntBits(value));
    }

    private static int readContextObjectId(byte[] packet, Cursor cursor, int tag) {
        expectTag(packet, cursor, tag, 4, true);
        return readInt(packet, cursor);
    }

    private static int readContextUnsigned(byte[] packet, Cursor cursor, int tag) {
        int length = expectPrimitiveTag(packet, cursor, tag, true);
        return readUnsigned(packet, cursor, length);
    }

    private static float readApplicationReal(byte[] packet, Cursor cursor) {
        int tagByte = readByte(packet, cursor);
        if (tagByte != 0x44) {
            throw new IllegalArgumentException("Expected BACnet REAL");
        }
        return Float.intBitsToFloat(readInt(packet, cursor));
    }

    private static int expectPrimitiveTag(byte[] packet, Cursor cursor, int tag, boolean context) {
        int tagByte = readByte(packet, cursor);
        int actualTag = (tagByte >>> 4) & 0x0F;
        int length = tagByte & 0x07;
        boolean actualContext = (tagByte & 0x08) != 0;
        if (actualTag != tag || actualContext != context) {
            throw new IllegalArgumentException("Unexpected BACnet tag");
        }
        if (length == 5) {
            return readByte(packet, cursor);
        }
        return length;
    }

    private static void expectTag(byte[] packet, Cursor cursor, int tag, int length, boolean context) {
        int actualLength = expectPrimitiveTag(packet, cursor, tag, context);
        if (actualLength != length) {
            throw new IllegalArgumentException("Unexpected BACnet tag length");
        }
    }

    private static void expectByte(byte[] packet, Cursor cursor, int expected) {
        int actual = readByte(packet, cursor);
        if (actual != expected) {
            throw new IllegalArgumentException("Unexpected BACnet marker");
        }
    }

    private static int readByte(byte[] packet, Cursor cursor) {
        if (cursor.offset >= cursor.end) {
            throw new IllegalArgumentException("Unexpected end of BACnet packet");
        }
        return packet[cursor.offset++] & 0xFF;
    }

    private static int readUnsigned(byte[] packet, Cursor cursor, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | readByte(packet, cursor);
        }
        return value;
    }

    private static int readInt(byte[] packet, Cursor cursor) {
        int value = ByteBuffer.wrap(packet, cursor.offset, 4).getInt();
        cursor.offset += 4;
        return value;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static byte[] unsignedBytes(int value) {
        if (value < 0x100) {
            return new byte[] { (byte) value };
        }
        if (value < 0x10000) {
            return new byte[] { (byte) (value >>> 8), (byte) value };
        }
        if (value < 0x1000000) {
            return new byte[] { (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
        }
        return new byte[] {
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        };
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("EOF reading BACnet MS/TP frame");
            }
            offset += read;
        }
        return buffer;
    }

    private static final class Cursor {
        int offset;
        final int end;

        Cursor(int offset, int end) {
            this.offset = offset;
            this.end = end;
        }
    }

    public sealed interface Message permits
            TokenFrame, ReadPropertyRequest, ReadPropertyAck, WritePropertyRequest, SimpleAck {
    }

    public record TokenFrame(int destination, int source) implements Message {
    }

    public record ReadPropertyRequest(int invokeId, int objectId, int propertyId, int destination, int source)
            implements Message {
    }

    public record ReadPropertyAck(int invokeId, int objectId, int propertyId, float value, int destination, int source)
            implements Message {
    }

    public record WritePropertyRequest(
            int invokeId, int objectId, int propertyId, float value, int destination, int source
    ) implements Message {
    }

    public record SimpleAck(int invokeId, int serviceChoice, int destination, int source) implements Message {
    }
}
