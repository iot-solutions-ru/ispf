package com.ispf.driver.lorawan;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Parsed LoRaWAN NS/AS lab point — DevEUI token for uplink poll / downlink write.
 * <p>
 * Accepted forms: {@code AABBCCDDEEFF0011}, {@code deveui:AABBCCDDEEFF0011}.
 */
record LorawanPoint(String deveui) {

    static LorawanPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("LoRaWAN lab point mapping is blank");
        }
        String normalized = mapping.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("deveui:")) {
            normalized = normalized.substring("deveui:".length()).trim();
        }
        String hex = normalized.toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.isEmpty() || (hex.length() % 2) != 0 || !hex.matches("[0-9A-F]+")) {
            throw new DriverException("Invalid LoRaWAN DevEUI mapping: " + mapping);
        }
        return new LorawanPoint(hex);
    }
}
