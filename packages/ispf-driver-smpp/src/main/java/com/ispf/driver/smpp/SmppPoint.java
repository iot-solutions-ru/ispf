package com.ispf.driver.smpp;

import java.util.Locale;

/**
 * Point mapping: {@code bind} for status or {@code destination:message} to submit SMS.
 */
public record SmppPoint(SmppMode mode, String destination, String message) {

    public enum SmppMode {
        BIND,
        SUBMIT
    }

    public static SmppPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SMPP point mapping is blank");
        }
        String trimmed = raw.trim();
        if ("bind".equalsIgnoreCase(trimmed)) {
            return new SmppPoint(SmppMode.BIND, null, null);
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String destination = trimmed.substring(0, colon).trim();
            String message = trimmed.substring(colon + 1);
            if (destination.isBlank() || message.isBlank()) {
                throw new IllegalArgumentException("SMPP submit mapping requires destination:message");
            }
            return new SmppPoint(SmppMode.SUBMIT, destination, message);
        }
        throw new IllegalArgumentException("Unknown SMPP point mapping: " + trimmed);
    }
}
