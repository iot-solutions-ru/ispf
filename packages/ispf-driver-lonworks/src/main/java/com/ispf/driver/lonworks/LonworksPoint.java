package com.ispf.driver.lonworks;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Parsed LonWorks LonTalk-IP gateway lab network-variable point.
 * <p>
 * Accepted forms: {@code nviTemp}, {@code nvoSetpoint}, {@code nvi:temp}, {@code nvo:setpoint},
 * {@code nv:1}, {@code nv:temp}.
 */
record LonworksPoint(String nvName) {

    static LonworksPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("LonWorks gateway point mapping is blank");
        }
        String normalized = mapping.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("nvi:") || lower.startsWith("nvo:") || lower.startsWith("nv:")) {
                int colon = lower.indexOf(':');
                String prefix = lower.substring(0, colon);
                String rest = normalized.substring(colon + 1).trim();
                if (rest.isEmpty()) {
                    throw new DriverException("LonWorks gateway point mapping missing NV name: " + mapping);
                }
                // Preserve colon form as gateway token (e.g. nvi:temp, nv:1).
                return new LonworksPoint(prefix + ":" + rest);
            }
            if (lower.matches("nv\\d+")) {
                return new LonworksPoint(lower);
            }
            // bare names: nviTemp, nvoSetpoint, temp, …
            return new LonworksPoint(normalized);
        } catch (RuntimeException e) {
            throw new DriverException("Invalid LonWorks gateway point mapping: " + mapping, e);
        }
    }

    /** Canonical ASCII token exchanged with the gateway (e.g. {@code nviTemp}, {@code nv:1}). */
    String gatewayToken() {
        return nvName;
    }
}
