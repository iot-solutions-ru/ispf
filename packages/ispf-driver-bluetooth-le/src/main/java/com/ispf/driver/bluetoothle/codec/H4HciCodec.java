package com.ispf.driver.bluetoothle.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Bluetooth H4 UART transport framing for HCI Command and Event packets over TCP.
 * <p>
 * Command: packet type {@code 0x01}, opcode little-endian, parameter length, parameters.
 * Event: packet type {@code 0x04}, event code, parameter length, parameters.
 * <p>
 * Clean-room Apache-2.0, JDK only — not a BLE radio and not a full GATT client.
 */
public final class H4HciCodec {

    public static final int PACKET_COMMAND = 0x01;
    public static final int PACKET_EVENT = 0x04;

    /** HCI_Reset (OGF 0x03 / OCF 0x0003) → opcode {@code 0x0C03}. */
    public static final int OPCODE_RESET = 0x0C03;

    /** HCI_Read_BD_ADDR (OGF 0x04 / OCF 0x0009) → opcode {@code 0x1009}. */
    public static final int OPCODE_READ_BD_ADDR = 0x1009;

    public static final int EVENT_COMMAND_COMPLETE = 0x0E;

    private H4HciCodec() {
    }

    /**
     * HCI_Reset over H4: {@code 01 03 0C 00}
     * (type {@code 0x01}, opcode {@code 0x0C03} LE, length {@code 0}).
     */
    public static byte[] encodeReset() {
        return encodeCommand(OPCODE_RESET, new byte[0]);
    }

    /**
     * HCI_Read_BD_ADDR over H4: {@code 01 09 10 00}
     * (type {@code 0x01}, opcode {@code 0x1009} LE, length {@code 0}).
     */
    public static byte[] encodeReadBdAddr() {
        return encodeCommand(OPCODE_READ_BD_ADDR, new byte[0]);
    }

    public static byte[] encodeCommand(int opcode, byte[] parameters) {
        byte[] params = parameters == null ? new byte[0] : parameters;
        if (params.length > 0xFF) {
            throw new IllegalArgumentException("HCI command parameter length exceeds uint8");
        }
        byte[] frame = new byte[4 + params.length];
        frame[0] = (byte) PACKET_COMMAND;
        frame[1] = (byte) (opcode & 0xFF);
        frame[2] = (byte) ((opcode >> 8) & 0xFF);
        frame[3] = (byte) params.length;
        System.arraycopy(params, 0, frame, 4, params.length);
        return frame;
    }

    /**
     * Reads one H4 HCI Event packet ({@code 04} + event + length + params).
     * Returns a defensive copy of the full packet including the type byte.
     */
    public static byte[] readEventPacket(InputStream in) throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new EOFException("EOF before H4 HCI event packet type");
        }
        if (type != PACKET_EVENT) {
            throw new IOException(String.format(Locale.ROOT,
                    "Expected H4 HCI event packet type 0x04, got 0x%02X", type & 0xFF));
        }
        int eventCode = in.read();
        int length = in.read();
        if (eventCode < 0 || length < 0) {
            throw new EOFException("EOF in H4 HCI event header");
        }
        byte[] params = readFully(in, length);
        byte[] packet = new byte[3 + params.length];
        packet[0] = (byte) PACKET_EVENT;
        packet[1] = (byte) eventCode;
        packet[2] = (byte) length;
        System.arraycopy(params, 0, packet, 3, params.length);
        return packet;
    }

    /**
     * Parses a Command Complete event and returns status (0 = success).
     * Layout after type: {@code 0E len num_packets opcode_le status …}.
     */
    public static int commandCompleteStatus(byte[] eventPacket, int expectedOpcode) throws IOException {
        if (eventPacket == null || eventPacket.length < 7) {
            throw new IOException("HCI Command Complete event too short");
        }
        if ((eventPacket[0] & 0xFF) != PACKET_EVENT
                || (eventPacket[1] & 0xFF) != EVENT_COMMAND_COMPLETE) {
            throw new IOException("Not an HCI Command Complete event");
        }
        int length = eventPacket[2] & 0xFF;
        if (eventPacket.length < 3 + length || length < 4) {
            throw new IOException("HCI Command Complete length mismatch");
        }
        int opcode = (eventPacket[4] & 0xFF) | ((eventPacket[5] & 0xFF) << 8);
        if (opcode != (expectedOpcode & 0xFFFF)) {
            throw new IOException(String.format(Locale.ROOT,
                    "HCI Command Complete opcode 0x%04X, expected 0x%04X",
                    opcode, expectedOpcode & 0xFFFF));
        }
        return eventPacket[6] & 0xFF;
    }

    /**
     * Extracts BD_ADDR from a successful Read_BD_ADDR Command Complete
     * ({@code 04 0E 0A 01 09 10 00} + 6 address octets, controller order).
     */
    public static String bdAddrFromCommandComplete(byte[] eventPacket) throws IOException {
        int status = commandCompleteStatus(eventPacket, OPCODE_READ_BD_ADDR);
        if (status != 0) {
            throw new IOException(String.format(Locale.ROOT,
                    "HCI Read_BD_ADDR status 0x%02X", status));
        }
        if (eventPacket.length < 13) {
            throw new IOException("HCI Read_BD_ADDR Command Complete missing BD_ADDR");
        }
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                sb.append(':');
            }
            // HCI returns BD_ADDR least-significant octet first.
            int octet = eventPacket[12 - i] & 0xFF;
            sb.append(String.format(Locale.ROOT, "%02X", octet));
        }
        return sb.toString();
    }

    /** Command Complete for successful HCI_Reset: {@code 04 0E 04 01 03 0C 00}. */
    public static byte[] resetCommandCompleteLiteral() {
        return new byte[] {
                (byte) PACKET_EVENT,
                (byte) EVENT_COMMAND_COMPLETE,
                0x04,
                0x01,
                0x03, 0x0C,
                0x00
        };
    }

    public static boolean equalsPacket(byte[] actual, byte[] expected) {
        return Arrays.equals(actual, expected);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new EOFException("EOF reading H4 HCI event parameters");
            }
            offset += n;
        }
        return buf;
    }
}
