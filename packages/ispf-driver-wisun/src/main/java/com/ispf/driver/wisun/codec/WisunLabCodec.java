package com.ispf.driver.wisun.codec;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Wi-SUN border-router CoAP lab dialect over TCP (ASCII CoAP-shaped lines).
 * <p>
 * Not a Wi-SUN FAN PHY / FAN stack. Clean-room Apache-2.0, JDK only.
 */
public final class WisunLabCodec {

    private WisunLabCodec() {
    }

    public static byte[] encodeGet(String path) {
        return ("GET " + path + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodePut(String path, float value) {
        return ("PUT " + path + " " + Float.toString(value) + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static float parseContent(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty Wi-SUN CoAP lab response");
        }
        String trimmed = line.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR") || upper.startsWith("4.") || upper.startsWith("5.")) {
            throw new IllegalArgumentException("Wi-SUN CoAP lab error: " + trimmed);
        }
        // "2.05 Content 21.5" or "2.05 21.5" or "OK 21.5"
        String[] parts = trimmed.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                return Float.parseFloat(parts[i]);
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        throw new IllegalArgumentException("Wi-SUN CoAP lab response missing value: " + trimmed);
    }

    public static void parseChangedAck(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty Wi-SUN CoAP lab write ack");
        }
        String trimmed = line.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR") || upper.startsWith("4.") || upper.startsWith("5.")) {
            throw new IllegalArgumentException("Wi-SUN CoAP lab write error: " + trimmed);
        }
        if (!(upper.startsWith("2.04") || upper.startsWith("2.01") || upper.startsWith("OK"))) {
            throw new IllegalArgumentException("Wi-SUN CoAP lab unexpected write ack: " + trimmed);
        }
    }
}
