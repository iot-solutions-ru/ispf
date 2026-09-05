package com.ispf.driver.bacnetmstp.codec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * BVLC-less BACnet APDU lab codec framed over TCP for an MS/TP gateway lab.
 * <p>
 * Frames are {@code uint16 BE length + NPDU(version,control) + APDU}. This is not a native
 * RS-485 MS/TP master. Clean-room Apache-2.0, JDK only.
 */
public final class BacnetMstpLabCodec {

    public static final int SERVICE_READ_PROPERTY = 12;
    public static final int SERVICE_WRITE_PROPERTY = 15;
    public static final int PRESENT_VALUE = 85;

    private static final int NPDU_VERSION = 0x01;
    private static final int NPDU_EXPECTING_REPLY = 0x04;
    private static final int PDU_CONFIRMED_REQUEST = 0x00;
    private static final int PDU_SIMPLE_ACK = 0x20;
    private static final int PDU_COMPLEX_ACK = 0x30;
    private static final int MAX_APDU = 0x05;

    private BacnetMstpLabCodec() {
    }

    public static byte[] encodeReadProperty(int invokeId, int encodedObjectId, int propertyId) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_CONFIRMED_REQUEST);
        apdu.write(MAX_APDU);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_READ_PROPERTY);
        writeContextObjectId(apdu, 0, encodedObjectId);
        writeContextUnsigned(apdu, 1, propertyId);
        return wrapFrame(apdu.toByteArray(), true);
    }

    public static byte[] encodeReadPropertyAck(int invokeId, int encodedObjectId, int propertyId, float value) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_COMPLEX_ACK);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_READ_PROPERTY);
        writeContextObjectId(apdu, 0, encodedObjectId);
        writeContextUnsigned(apdu, 1, propertyId);
        apdu.write(0x3E);
        writeApplicationReal(apdu, value);
        apdu.write(0x3F);
        return wrapFrame(apdu.toByteArray(), false);
    }

    public static byte[] encodeWriteProperty(int invokeId, int encodedObjectId, int propertyId, float value) {
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
        return wrapFrame(apdu.toByteArray(), true);
    }

    public static byte[] encodeSimpleAck(int invokeId, int serviceChoice) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_SIMPLE_ACK);
        apdu.write(invokeId & 0xFF);
        apdu.write(serviceChoice & 0xFF);
        return wrapFrame(apdu.toByteArray(), false);
    }

    public static Message decode(byte[] frame) {
        if (frame == null || frame.length < 4) {
            throw new IllegalArgumentException("BACnet MS/TP lab frame too short");
        }
        int length = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        if (frame.length < 2 + length || length < 2) {
            throw new IllegalArgumentException("Incomplete BACnet MS/TP lab frame");
        }
        if ((frame[2] & 0xFF) != NPDU_VERSION) {
            throw new IllegalArgumentException("Unsupported NPDU version");
        }
        int apduOffset = 4;
        int pduType = frame[apduOffset] & 0xF0;
        if (pduType == PDU_CONFIRMED_REQUEST) {
            int invokeId = frame[apduOffset + 2] & 0xFF;
            int service = frame[apduOffset + 3] & 0xFF;
            Cursor cursor = new Cursor(apduOffset + 4, 2 + length);
            int objectId = readContextObjectId(frame, cursor, 0);
            int property = readContextUnsigned(frame, cursor, 1);
            if (service == SERVICE_READ_PROPERTY) {
                return new ReadPropertyRequest(invokeId, objectId, property);
            }
            if (service == SERVICE_WRITE_PROPERTY) {
                expectByte(frame, cursor, 0x3E);
                float value = readApplicationReal(frame, cursor);
                expectByte(frame, cursor, 0x3F);
                return new WritePropertyRequest(invokeId, objectId, property, value);
            }
        }
        if (pduType == PDU_COMPLEX_ACK) {
            int invokeId = frame[apduOffset + 1] & 0xFF;
            int service = frame[apduOffset + 2] & 0xFF;
            if (service == SERVICE_READ_PROPERTY) {
                Cursor cursor = new Cursor(apduOffset + 3, 2 + length);
                int objectId = readContextObjectId(frame, cursor, 0);
                int property = readContextUnsigned(frame, cursor, 1);
                expectByte(frame, cursor, 0x3E);
                float value = readApplicationReal(frame, cursor);
                expectByte(frame, cursor, 0x3F);
                return new ReadPropertyAck(invokeId, objectId, property, value);
            }
        }
        if (pduType == PDU_SIMPLE_ACK) {
            return new SimpleAck(frame[apduOffset + 1] & 0xFF, frame[apduOffset + 2] & 0xFF);
        }
        throw new IllegalArgumentException("Unsupported BACnet MS/TP lab APDU");
    }

    public static byte[] wrapFrame(byte[] apdu, boolean expectingReply) {
        int bodyLength = 2 + apdu.length;
        ByteBuffer buffer = ByteBuffer.allocate(2 + bodyLength);
        buffer.putShort((short) bodyLength);
        buffer.put((byte) NPDU_VERSION);
        buffer.put((byte) (expectingReply ? NPDU_EXPECTING_REPLY : 0));
        buffer.put(apdu);
        return buffer.array();
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

    private static final class Cursor {
        int offset;
        final int end;

        Cursor(int offset, int end) {
            this.offset = offset;
            this.end = end;
        }
    }

    public sealed interface Message permits ReadPropertyRequest, ReadPropertyAck, WritePropertyRequest, SimpleAck {
    }

    public record ReadPropertyRequest(int invokeId, int objectId, int propertyId) implements Message {
    }

    public record ReadPropertyAck(int invokeId, int objectId, int propertyId, float value) implements Message {
    }

    public record WritePropertyRequest(int invokeId, int objectId, int propertyId, float value) implements Message {
    }

    public record SimpleAck(int invokeId, int serviceChoice) implements Message {
    }

    public static byte[] stripToPayload(byte[] frame) {
        return Arrays.copyOf(frame, frame.length);
    }
}
