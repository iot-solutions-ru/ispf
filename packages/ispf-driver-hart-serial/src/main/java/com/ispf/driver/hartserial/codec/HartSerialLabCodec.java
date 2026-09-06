package com.ispf.driver.hartserial.codec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Minimal HART serial-gateway lab codec: length-prefixed TCP wrapping a short-frame HART PDU subset.
 * <p>
 * Not an FSK modem / HART FSK PHY and not a full HCF stack. Clean-room Apache-2.0, JDK only.
 * Frame: {@code uint16 BE length + HART PDU}.
 */
public final class HartSerialLabCodec {

    public static final int CMD_READ_PV = 1;
    public static final int CMD_READ_DYNAMIC = 3;

    private HartSerialLabCodec() {
    }

    public static byte[] wrapPdu(byte[] hartPdu) {
        byte[] body = hartPdu == null ? new byte[0] : hartPdu;
        ByteBuffer buffer = ByteBuffer.allocate(2 + body.length);
        buffer.putShort((short) (body.length & 0xFFFF));
        buffer.put(body);
        return buffer.array();
    }

    public static byte[] unwrapPdu(byte[] frame) {
        if (frame == null || frame.length < 2) {
            throw new IllegalArgumentException("HART serial-gateway frame too short");
        }
        int length = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        if (frame.length < 2 + length) {
            throw new IllegalArgumentException("Incomplete HART serial-gateway frame");
        }
        return Arrays.copyOfRange(frame, 2, 2 + length);
    }

    /** Master short-frame command request (polling address). */
    public static byte[] encodeHartCommand(int deviceAddress, int command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        out.write(0x02); // short-frame master STX
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(0x00); // byte count
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
    }

    /**
     * Slave short-frame response for command 1 (PV) or command 3 (dynamic vars lab).
     * Includes response code + device status then IEEE float PV (and optional extras for cmd 3).
     */
    public static byte[] encodeHartPvResponse(int deviceAddress, int command, float pv) {
        ByteArrayOutputStream data = new ByteArrayOutputStream(24);
        data.write(0x00); // response code
        data.write(0x00); // device status
        if (command == CMD_READ_DYNAMIC) {
            writeFloat(data, 4.0f); // loop current mA (lab)
            data.write(0x27); // units code (lab: degrees C)
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
            data.write(0x27);
            writeFloat(data, pv);
        } else {
            data.write(0x27); // units
            writeFloat(data, pv);
        }
        byte[] payload = data.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + payload.length);
        out.write(0x06); // short-frame slave STX
        out.write(deviceAddress & 0x3F);
        out.write(command & 0xFF);
        out.write(payload.length & 0xFF);
        out.writeBytes(payload);
        byte[] withoutChecksum = out.toByteArray();
        out.write(checksum(withoutChecksum));
        return out.toByteArray();
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
        int dataStart = 4; // after delim, addr, cmd, count — data includes rc+status
        if (command == CMD_READ_DYNAMIC) {
            // rc, status, current(4), units(1), pv(4)
            int pvOffset = dataStart + 2 + 4 + 1;
            return readFloat(responsePdu, pvOffset);
        }
        // cmd 1: rc, status, units(1), pv(4)
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

    public record HartCommand(int address, int command, int byteCount) {
    }
}
