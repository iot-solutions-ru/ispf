package com.ispf.driver.wmbus;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Wireless M-Bus TCP gateway lab point: {@code meter:1} or {@code id:HEX}.
 */
record WmbusPoint(Kind kind, String key) {

    enum Kind {
        METER_INDEX,
        DEVICE_ID
    }

    static WmbusPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("wM-Bus point mapping is blank");
        }
        String normalized = mapping.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.startsWith("meter:")) {
                String index = normalized.substring(6).trim();
                Integer.parseInt(index);
                return new WmbusPoint(Kind.METER_INDEX, index);
            }
            if (normalized.startsWith("id:")) {
                String hex = normalized.substring(3).trim().toUpperCase(Locale.ROOT);
                if (hex.isEmpty() || (hex.length() % 2) != 0 || !hex.matches("[0-9A-F]+")) {
                    throw new DriverException("wM-Bus id must be even-length hex: " + mapping);
                }
                return new WmbusPoint(Kind.DEVICE_ID, hex);
            }
            // bare decimal meter index
            if (normalized.matches("\\d+")) {
                return new WmbusPoint(Kind.METER_INDEX, normalized);
            }
            throw new DriverException("Unsupported wM-Bus mapping (expected meter:N or id:HEX): " + mapping);
        } catch (NumberFormatException e) {
            throw new DriverException("Invalid wM-Bus mapping: " + mapping, e);
        }
    }

    String pollToken() {
        return kind == Kind.METER_INDEX ? "meter:" + key : "id:" + key;
    }
}
