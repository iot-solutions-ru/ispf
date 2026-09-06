package com.ispf.driver.isa100.codec;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ISA100 gateway ASCII/JSON lab dialect over TCP.
 * <p>
 * Not an ISA100.11a RF / Wireless Compliance Institute stack. Clean-room Apache-2.0, JDK only.
 */
public final class Isa100LabCodec {

    private static final Pattern JSON_VALUE = Pattern.compile("\"value\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

    private Isa100LabCodec() {
    }

    public static byte[] encodeGet(String path) {
        return ("GET " + path + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeSet(String path, float value) {
        return ("SET " + path + " " + Float.toString(value) + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static float parseValue(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty ISA100 lab response");
        }
        String trimmed = line.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IllegalArgumentException("ISA100 lab error: " + trimmed);
        }
        if (trimmed.startsWith("{")) {
            Matcher matcher = JSON_VALUE.matcher(trimmed);
            if (!matcher.find()) {
                throw new IllegalArgumentException("ISA100 lab JSON missing value: " + trimmed);
            }
            return Float.parseFloat(matcher.group(1));
        }
        if (upper.startsWith("OK")) {
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 2) {
                throw new IllegalArgumentException("ISA100 lab response missing value: " + trimmed);
            }
            return Float.parseFloat(parts[parts.length - 1]);
        }
        return Float.parseFloat(trimmed);
    }

    public static void parseOkAck(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty ISA100 lab write ack");
        }
        String trimmed = line.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ERR")) {
            throw new IllegalArgumentException("ISA100 lab write error: " + trimmed);
        }
        if (!(upper.startsWith("OK") || trimmed.contains("\"ok\":true"))) {
            throw new IllegalArgumentException("ISA100 lab unexpected write ack: " + trimmed);
        }
    }
}
