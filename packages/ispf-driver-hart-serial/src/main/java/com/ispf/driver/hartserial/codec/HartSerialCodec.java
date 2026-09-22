package com.ispf.driver.hartserial.codec;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * HART short-frame codec for a TCP serial gateway: five {@code 0xFF} preamble octets then the PDU.
 * <p>
 * Master request: STX {@code 0x02}, address, command, byte count, XOR checksum.
 * Not an FSK modem / HART FSK PHY and not a full HCF stack. Clean-room Apache-2.0, JDK only.
 */
public final class HartSerialCodec {

    public static final int PREAMBLE_LENGTH = 5;
    public static final int PREAMBLE_OCTET = 0xFF;

    public static final int CMD_READ_PV = 1;
    public static final int CMD_READ_DYNAMIC = 3;

    private HartSerialCodec() {
    }

    /** Five preamble octets followed by the short-frame master command. */
    public static byte[] encodeRequest(int deviceAddress, int command) {
        byte[] hart = encodeHartCommand(deviceAddress, command);
        byte[] frame = new byte[PREAMBLE_LENGTH + hart.length];
        Arrays.fill(frame, 0, PREAMBLE_LENGTH, (byte) PREAMBLE_OCTET);
        System.arraycopy(hart, 0, frame, PREAMBLE_LENGTH, hart.length);
        return frame;
    }

    /** Master short-frame command request (polling address), without preamble. */
    public static byte[] encodeHartCommand(int deviceAddress, int command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5);
        out.write(0x02);
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(0x00);
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    /**
     * Slave short-frame response for command 1 (PV) or command 3 (dynamic variables),
     * preceded by five preamble octets.
     */
    public static byte[] encodeResponse(int deviceAddress, int command, float pv) {
        byte[] hart = encodeHartPvResponse(deviceAddress, command, pv);
        byte[] frame = new byte[PREAMBLE_LENGTH + hart.length];
        Arrays.fill(frame, 0, PREAMBLE_LENGTH, (byte) PREAMBLE_OCTET);
        System.arraycopy(hart, 0, frame, PREAMBLE_LENGTH, hart.length);
        return frame;
    }

    public static byte[] encodeHartPvResponse(int deviceAddress, int command, float pv) {
        ByteArrayOutputStream data = new ByteArrayOutputStream(24);
        data.write(0x00);
        data.write(0x00);
        if (command == CMD_READ_DYNAMIC) {
            writeFloat(data, 4.0f);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
        } else {
            data.write(0x27);
            writeFloat(data, pv);
        }
        byte[] payload = data.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + payload.length);
        out.write(0x06);
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(payload.length & 0xFF);
        out.writeBytes(payload);
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    public static byte[] readHartPdu(InputStream in) throws IOException {
        int delimiter;
        do {
            delimiter = in.read();
            if (delimiter < 0) {
                throw new EOFException("EOF reading HART serial preamble");
            }
        } while (delimiter == PREAMBLE_OCTET);

        byte[] header = readFully(in, 3);
        int byteCount = header[2] & 0xFF;
        byte[] dataAndChecksum = readFully(in, byteCount + 1);
        byte[] pdu = new byte[4 + byteCount + 1];
        pdu[0] = (byte) delimiter;
        System.arraycopy(header, 0, pdu, 1, 3);
        System.arraycopy(dataAndChecksum, 0, pdu, 4, dataAndChecksum.length);
        return pdu;
    }

    public static HartCommand parseHartCommand(byte[] pdu) {
        if (pdu == null || pdu.length < 5) {
            throw new IllegalArgumentException("HART PDU too short");
        }
        int address = pdu[1] & 0x3F;
        int command = pdu[2] & 0xFF;
        int byteCount = pdu[3] & 0xFF;
        return new HartCommand(address, command, byteCount);
    }

    public static float extractPv(byte[] responsePdu) {
        if (responsePdu == null || responsePdu.length < 10) {
            throw new IllegalArgumentException("HART response too short for PV");
        }
        int command = responsePdu[2] & 0xFF;
        int dataStart = 4;
        if (command == CMD_READ_DYNAMIC) {
            int pvOffset = dataStart + 2 + 4 + 1;
            return readFloat(responsePdu, pvOffset);
        }
        int pvOffset = dataStart + 2 + 1;
        return readFloat(responsePdu, pvOffset);
    }

    public static int checksum(byte[] bytes) {
        int xor = 0;
        for (byte value : bytes) {
            xor ^= value & 0xFF;
        }
        return xor & 0xFF;
    }

    private static void writeFloat(ByteArrayOutputStream out, float value) {
        int bits = Float.floatToIntBits(value);
        out.write((bits >>> 24) & 0xFF);
        out.write((bits >>> 16) & 0xFF);
        out.write((bits >>> 8) & 0xFF);
        out.write(bits & 0xFF);
    }

    private static float readFloat(byte[] bytes, int offset) {
        int bits = ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
        return Float.intBitsToFloat(bits);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("EOF reading HART serial PDU");
            }
            offset += read;
        }
        return buffer;
    }

    public record HartCommand(int address, int command, int byteCount) {
    }
}
