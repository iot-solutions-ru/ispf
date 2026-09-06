package com.ispf.driver.wirelesshart.codec;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * WirelessHART gateway TCP lab dialect — ASCII cmd/PV lines (HART-IP–shaped).
 * <p>
 * Not an 802.15.4 WirelessHART radio / HCF stack. Clean-room Apache-2.0, JDK only.
 */
public final class WirelesshartLabCodec {

    public static final int CMD_READ_PV = 1;
    public static final int CMD_READ_DYNAMIC = 3;

    private WirelesshartLabCodec() {
    }

    public static byte[] encodeGet(int deviceAddress, int command) {
        return ("GET device:" + deviceAddress + ":cmd:" + command + "\r\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeSet(int deviceAddress, int command, float value) {
        return ("SET device:" + deviceAddress + ":cmd:" + command + " " + Float.toString(value) + "\r\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    public static float parseOkValue(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty WirelessHART lab response");
        }
        String trimmed = line.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IllegalArgumentException("WirelessHART lab error: " + trimmed);
        }
        if (!upper.startsWith("OK")) {
            throw new IllegalArgumentException("WirelessHART lab unexpected response: " + trimmed);
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("WirelessHART lab response missing value: " + trimmed);
        }
        return Float.parseFloat(parts[parts.length - 1]);
    }

    public static void parseOkAck(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty WirelessHART lab write ack");
        }
        String trimmed = line.trim();
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("ERR")) {
            throw new IllegalArgumentException("WirelessHART lab write error: " + trimmed);
        }
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("OK")) {
            throw new IllegalArgumentException("WirelessHART lab unexpected write ack: " + trimmed);
        }
    }
}
