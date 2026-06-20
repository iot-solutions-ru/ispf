package com.ispf.driver.mbus;

/**
 * Point mapping: {@code primaryAddress:secondaryAddress:register}.
 */
public record MbusPoint(int primaryAddress, int secondaryAddress, String register) {

    public static MbusPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("M-Bus point mapping is blank");
        }
        String[] parts = raw.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("M-Bus point mapping must be primary:secondary:register");
        }
        return new MbusPoint(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                parts[2].trim()
        );
    }
}
