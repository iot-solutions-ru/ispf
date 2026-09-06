package com.ispf.driver.hartip;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Parsed HART-IP lab point: primary variable / universal command against a polling address.
 * <p>
 * Accepted forms: {@code pv}, {@code cmd:1}, {@code cmd:3}, {@code device:0},
 * {@code device:0:cmd:1}, {@code 0:1}.
 */
record HartIpPoint(int deviceAddress, int command) {

    static HartIpPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("HART-IP point mapping is blank");
        }
        String normalized = mapping.trim().toLowerCase(Locale.ROOT);
        try {
            if ("pv".equals(normalized) || "primary".equals(normalized) || "primary-variable".equals(normalized)) {
                return new HartIpPoint(0, 1);
            }
            if (normalized.startsWith("cmd:")) {
                return new HartIpPoint(0, Integer.parseInt(normalized.substring(4).trim()));
            }
            if (normalized.startsWith("device:")) {
                String rest = normalized.substring(7).trim();
                int colon = rest.indexOf(':');
                if (colon < 0) {
                    return new HartIpPoint(Integer.parseInt(rest), 1);
                }
                int device = Integer.parseInt(rest.substring(0, colon).trim());
                String after = rest.substring(colon + 1).trim();
                if (after.startsWith("cmd:")) {
                    return new HartIpPoint(device, Integer.parseInt(after.substring(4).trim()));
                }
                return new HartIpPoint(device, Integer.parseInt(after));
            }
            String[] parts = normalized.split(":");
            if (parts.length == 2) {
                return new HartIpPoint(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
            throw new DriverException("Unsupported HART-IP point mapping: " + mapping);
        } catch (NumberFormatException e) {
            throw new DriverException("Invalid HART-IP point mapping: " + mapping, e);
        }
    }
}
