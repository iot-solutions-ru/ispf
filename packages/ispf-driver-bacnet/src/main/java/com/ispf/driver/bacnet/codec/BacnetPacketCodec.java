package com.ispf.driver.bacnet.codec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Minimal BACnet/IP packet codec for BVLL Original-Unicast-NPDU packets.
 */
public final class BacnetPacketCodec {

    public static final int SERVICE_I_AM = 0;
    public static final int SERVICE_WHO_IS = 8;
    public static final int SERVICE_READ_PROPERTY = 12;
    public static final int SERVICE_WRITE_PROPERTY = 15;

    private static final int BVLC_TYPE_BACNET_IP = 0x81;
    private static final int BVLC_ORIGINAL_UNICAST_NPDU = 0x0A;
    private static final int NPDU_VERSION = 0x01;
    private static final int NPDU_EXPECTING_REPLY = 0x04;
    private static final int PDU_CONFIRMED_REQUEST = 0x00;
    private static final int PDU_SIMPLE_ACK = 0x20;
    private static final int PDU_COMPLEX_ACK = 0x30;
    private static final int PDU_UNCONFIRMED_REQUEST = 0x10;
    private static final int MAX_SEGMENTS_APDU_1476 = 0x05;

    private BacnetPacketCodec() {
    }

    public static byte[] encodeWhoIs() {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_UNCONFIRMED_REQUEST);
        apdu.write(SERVICE_WHO_IS);
        return wrapNpdu(apdu.toByteArray(), false);
    }

    public static byte[] encodeIAm(int deviceId) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_UNCONFIRMED_REQUEST);
        apdu.write(SERVICE_I_AM);
        writeApplicationObjectId(apdu, new BacnetObjectIdentifier(BacnetObjectType.DEVICE, deviceId));
        writeApplicationUnsigned(apdu, 1476);
        writeApplicationEnumerated(apdu, 3);
        writeApplicationUnsigned(apdu, 999);
        return wrapNpdu(apdu.toByteArray(), false);
    }

    public static byte[] encodeReadPropertyRequest(
            int invokeId,
            BacnetObjectIdentifier objectId,
            BacnetPropertyIdentifier property
    ) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_CONFIRMED_REQUEST);
        apdu.write(MAX_SEGMENTS_APDU_1476);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_READ_PROPERTY);
        writeContextObjectId(apdu, 0, objectId);
        writeContextUnsigned(apdu, 1, property.id());
        return wrapNpdu(apdu.toByteArray(), true);
    }

    public static byte[] encodeReadPropertyAck(int invokeId, ReadPropertyRequest request, BacnetValue value) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_COMPLEX_ACK);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_READ_PROPERTY);
        writeContextObjectId(apdu, 0, request.objectId());
        writeContextUnsigned(apdu, 1, request.property().id());
        apdu.write(0x3E);
        writeApplicationValue(apdu, value);
        apdu.write(0x3F);
        return wrapNpdu(apdu.toByteArray(), false);
    }

    public static byte[] encodeWritePropertyRequest(
            int invokeId,
            BacnetObjectIdentifier objectId,
            BacnetPropertyIdentifier property,
            BacnetValue value
    ) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_CONFIRMED_REQUEST);
        apdu.write(MAX_SEGMENTS_APDU_1476);
        apdu.write(invokeId & 0xFF);
        apdu.write(SERVICE_WRITE_PROPERTY);
        writeContextObjectId(apdu, 0, objectId);
        writeContextUnsigned(apdu, 1, property.id());
        apdu.write(0x3E);
        writeApplicationValue(apdu, value);
        apdu.write(0x3F);
        return wrapNpdu(apdu.toByteArray(), true);
    }

    public static byte[] encodeSimpleAck(int invokeId, int serviceChoice) {
        ByteArrayOutputStream apdu = new ByteArrayOutputStream();
        apdu.write(PDU_SIMPLE_ACK);
        apdu.write(invokeId & 0xFF);
        apdu.write(serviceChoice & 0xFF);
        return wrapNpdu(apdu.toByteArray(), false);
    }

    public static Message decode(byte[] packet, int length) throws BacnetException {
        if (length < 6) {
            throw new BacnetException("BACnet/IP packet too short");
        }
        if ((packet[0] & 0xFF) != BVLC_TYPE_BACNET_IP || (packet[1] & 0xFF) != BVLC_ORIGINAL_UNICAST_NPDU) {
            throw new BacnetException("Unsupported BACnet/IP BVLC function");
        }
        int declaredLength = unsigned16(packet, 2);
        if (declaredLength > length) {
            throw new BacnetException("Incomplete BACnet/IP packet");
        }
        if ((packet[4] & 0xFF) != NPDU_VERSION) {
            throw new BacnetException("Unsupported BACnet NPDU version");
        }
        int apduOffset = 6;
        int pduType = packet[apduOffset] & 0xF0;
        if (pduType == PDU_UNCONFIRMED_REQUEST) {
            int service = packet[apduOffset + 1] & 0xFF;
            if (service == SERVICE_WHO_IS) {
                return new WhoIsMessage();
            }
            if (service == SERVICE_I_AM) {
                return decodeIAm(packet, apduOffset + 2, declaredLength);
            }
        }
        if (pduType == PDU_CONFIRMED_REQUEST) {
            int invokeId = packet[apduOffset + 2] & 0xFF;
            int service = packet[apduOffset + 3] & 0xFF;
            if (service == SERVICE_READ_PROPERTY) {
                return decodeReadPropertyRequest(invokeId, packet, apduOffset + 4, declaredLength);
            }
            if (service == SERVICE_WRITE_PROPERTY) {
                return decodeWritePropertyRequest(invokeId, packet, apduOffset + 4, declaredLength);
            }
        }
        if (pduType == PDU_COMPLEX_ACK) {
            int invokeId = packet[apduOffset + 1] & 0xFF;
            int service = packet[apduOffset + 2] & 0xFF;
            if (service == SERVICE_READ_PROPERTY) {
                return decodeReadPropertyAck(invokeId, packet, apduOffset + 3, declaredLength);
            }
        }
        if (pduType == PDU_SIMPLE_ACK) {
            return new SimpleAckMessage(packet[apduOffset + 1] & 0xFF, packet[apduOffset + 2] & 0xFF);
        }
        throw new BacnetException("Unsupported BACnet APDU");
    }

    private static IAmMessage decodeIAm(byte[] packet, int offset, int end) throws BacnetException {
        Cursor cursor = new Cursor(offset, end);
        BacnetObjectIdentifier objectId = readApplicationObjectId(packet, cursor);
        return new IAmMessage(objectId.instance());
    }

    private static ReadPropertyRequestMessage decodeReadPropertyRequest(
            int invokeId,
            byte[] packet,
            int offset,
            int end
    ) throws BacnetException {
        Cursor cursor = new Cursor(offset, end);
        BacnetObjectIdentifier objectId = readContextObjectId(packet, cursor, 0);
        BacnetPropertyIdentifier property = BacnetPropertyIdentifier.fromId(readContextUnsigned(packet, cursor, 1));
        return new ReadPropertyRequestMessage(invokeId, new ReadPropertyRequest(objectId, property));
    }

    private static ReadPropertyAckMessage decodeReadPropertyAck(
            int invokeId,
            byte[] packet,
            int offset,
            int end
    ) throws BacnetException {
        Cursor cursor = new Cursor(offset, end);
        BacnetObjectIdentifier objectId = readContextObjectId(packet, cursor, 0);
        BacnetPropertyIdentifier property = BacnetPropertyIdentifier.fromId(readContextUnsigned(packet, cursor, 1));
        expectByte(packet, cursor, 0x3E);
        BacnetValue value = readApplicationValue(packet, cursor);
        expectByte(packet, cursor, 0x3F);
        return new ReadPropertyAckMessage(invokeId, new ReadPropertyRequest(objectId, property), value);
    }

    private static WritePropertyRequestMessage decodeWritePropertyRequest(
            int invokeId,
            byte[] packet,
            int offset,
            int end
    ) throws BacnetException {
        Cursor cursor = new Cursor(offset, end);
        BacnetObjectIdentifier objectId = readContextObjectId(packet, cursor, 0);
        BacnetPropertyIdentifier property = BacnetPropertyIdentifier.fromId(readContextUnsigned(packet, cursor, 1));
        expectByte(packet, cursor, 0x3E);
        BacnetValue value = readApplicationValue(packet, cursor);
        expectByte(packet, cursor, 0x3F);
        return new WritePropertyRequestMessage(invokeId, new WritePropertyRequest(objectId, property, value));
    }

    private static byte[] wrapNpdu(byte[] apdu, boolean expectsReply) {
        int length = 6 + apdu.length;
        ByteArrayOutputStream packet = new ByteArrayOutputStream(length);
        packet.write(BVLC_TYPE_BACNET_IP);
        packet.write(BVLC_ORIGINAL_UNICAST_NPDU);
        packet.write((length >>> 8) & 0xFF);
        packet.write(length & 0xFF);
        packet.write(NPDU_VERSION);
        packet.write(expectsReply ? NPDU_EXPECTING_REPLY : 0);
        packet.writeBytes(apdu);
        return packet.toByteArray();
    }

    private static void writeContextObjectId(ByteArrayOutputStream out, int tag, BacnetObjectIdentifier objectId) {
        out.write((tag << 4) | 0x0C);
        writeInt(out, objectId.encoded());
    }

    private static BacnetObjectIdentifier readContextObjectId(byte[] packet, Cursor cursor, int tag) throws BacnetException {
        expectTag(packet, cursor, tag, 4, true);
        return BacnetObjectIdentifier.decode(readInt(packet, cursor));
    }

    private static void writeApplicationObjectId(ByteArrayOutputStream out, BacnetObjectIdentifier objectId) {
        out.write(0xC4);
        writeInt(out, objectId.encoded());
    }

    private static BacnetObjectIdentifier readApplicationObjectId(byte[] packet, Cursor cursor) throws BacnetException {
        expectTag(packet, cursor, 12, 4, false);
        return BacnetObjectIdentifier.decode(readInt(packet, cursor));
    }

    private static void writeContextUnsigned(ByteArrayOutputStream out, int tag, int value) {
        byte[] encoded = unsignedBytes(value);
        out.write((tag << 4) | 0x08 | encoded.length);
        out.writeBytes(encoded);
    }

    private static int readContextUnsigned(byte[] packet, Cursor cursor, int tag) throws BacnetException {
        int length = expectPrimitiveTag(packet, cursor, tag, true);
        return readUnsigned(packet, cursor, length);
    }

    private static void writeApplicationValue(ByteArrayOutputStream out, BacnetValue value) {
        switch (value) {
            case BacnetValue.RealValue real -> {
                out.write(0x44);
                writeInt(out, Float.floatToIntBits(real.value()));
            }
            case BacnetValue.BinaryValue binary -> writeApplicationEnumerated(out, binary.active() ? 1 : 0);
            case BacnetValue.UnsignedValue unsigned -> writeApplicationUnsigned(out, unsigned.value());
        }
    }

    private static BacnetValue readApplicationValue(byte[] packet, Cursor cursor) throws BacnetException {
        int tagByte = readByte(packet, cursor);
        int tag = (tagByte >>> 4) & 0x0F;
        int length = tagByte & 0x07;
        if ((tagByte & 0x08) != 0) {
            throw new BacnetException("Expected BACnet application tag");
        }
        return switch (tag) {
            case 2 -> new BacnetValue.UnsignedValue(readUnsigned(packet, cursor, length));
            case 4 -> {
                if (length != 4) {
                    throw new BacnetException("Invalid BACnet REAL length: " + length);
                }
                yield new BacnetValue.RealValue(Float.intBitsToFloat(readInt(packet, cursor)));
            }
            case 9 -> new BacnetValue.BinaryValue(readUnsigned(packet, cursor, length) == 1);
            default -> throw new BacnetException("Unsupported BACnet application tag: " + tag);
        };
    }

    private static void writeApplicationUnsigned(ByteArrayOutputStream out, int value) {
        byte[] encoded = unsignedBytes(value);
        out.write(0x20 | encoded.length);
        out.writeBytes(encoded);
    }

    private static void writeApplicationEnumerated(ByteArrayOutputStream out, int value) {
        byte[] encoded = unsignedBytes(value);
        out.write(0x90 | encoded.length);
        out.writeBytes(encoded);
    }

    private static int expectPrimitiveTag(byte[] packet, Cursor cursor, int tag, boolean context) throws BacnetException {
        int tagByte = readByte(packet, cursor);
        int actualTag = (tagByte >>> 4) & 0x0F;
        int length = tagByte & 0x07;
        boolean actualContext = (tagByte & 0x08) != 0;
        if (actualTag != tag || actualContext != context) {
            throw new BacnetException("Unexpected BACnet tag");
        }
        if (length == 5) {
            return readByte(packet, cursor);
        }
        return length;
    }

    private static void expectTag(byte[] packet, Cursor cursor, int tag, int length, boolean context)
            throws BacnetException {
        int actualLength = expectPrimitiveTag(packet, cursor, tag, context);
        if (actualLength != length) {
            throw new BacnetException("Unexpected BACnet tag length");
        }
    }

    private static void expectByte(byte[] packet, Cursor cursor, int expected) throws BacnetException {
        int actual = readByte(packet, cursor);
        if (actual != expected) {
            throw new BacnetException("Unexpected BACnet marker");
        }
    }

    private static int readByte(byte[] packet, Cursor cursor) throws BacnetException {
        if (cursor.offset >= cursor.end) {
            throw new BacnetException("Unexpected end of BACnet packet");
        }
        return packet[cursor.offset++] & 0xFF;
    }

    private static int readUnsigned(byte[] packet, Cursor cursor, int length) throws BacnetException {
        if (length < 1 || length > 4 || cursor.offset + length > cursor.end) {
            throw new BacnetException("Invalid BACnet unsigned length");
        }
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | readByte(packet, cursor);
        }
        return value;
    }

    private static int readInt(byte[] packet, Cursor cursor) throws BacnetException {
        if (cursor.offset + 4 > cursor.end) {
            throw new BacnetException("Unexpected end of BACnet packet");
        }
        int value = ByteBuffer.wrap(packet, cursor.offset, 4).getInt();
        cursor.offset += 4;
        return value;
    }

    private static int unsigned16(byte[] packet, int offset) {
        return ((packet[offset] & 0xFF) << 8) | (packet[offset + 1] & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static byte[] unsignedBytes(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("BACnet unsigned value must be non-negative");
        }
        if (value <= 0xFF) {
            return new byte[] {(byte) value};
        }
        if (value <= 0xFFFF) {
            return new byte[] {(byte) (value >>> 8), (byte) value};
        }
        if (value <= 0xFF_FFFF) {
            return new byte[] {(byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
        }
        return new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    public sealed interface Message permits WhoIsMessage, IAmMessage, ReadPropertyRequestMessage,
            ReadPropertyAckMessage, WritePropertyRequestMessage, SimpleAckMessage {
    }

    public record WhoIsMessage() implements Message {
    }

    public record IAmMessage(int deviceId) implements Message {
    }

    public record ReadPropertyRequest(BacnetObjectIdentifier objectId, BacnetPropertyIdentifier property) {
    }

    public record ReadPropertyRequestMessage(int invokeId, ReadPropertyRequest request) implements Message {
    }

    public record ReadPropertyAckMessage(int invokeId, ReadPropertyRequest request, BacnetValue value)
            implements Message {
    }

    public record WritePropertyRequest(BacnetObjectIdentifier objectId, BacnetPropertyIdentifier property, BacnetValue value) {
    }

    public record WritePropertyRequestMessage(int invokeId, WritePropertyRequest request) implements Message {
    }

    public record SimpleAckMessage(int invokeId, int serviceChoice) implements Message {
    }

    private static final class Cursor {
        private int offset;
        private final int end;

        private Cursor(int offset, int end) {
            this.offset = offset;
            this.end = end;
        }
    }
}
