package com.ispf.driver.wisun;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Parsed Wi-SUN border-router CoAP lab point.
 * <p>
 * Accepted forms: {@code node:1}, {@code /nodes/1/value}, {@code coap:/nodes/1/value}.
 */
record WisunPoint(String path) {

    static WisunPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Wi-SUN lab point mapping is blank");
        }
        String normalized = mapping.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("coap:")) {
            normalized = normalized.substring("coap:".length()).trim();
            if (normalized.startsWith("//")) {
                int slash = normalized.indexOf('/', 2);
                normalized = slash >= 0 ? normalized.substring(slash) : "/";
            }
        }
        if (lower.startsWith("node:")) {
            String id = normalized.substring("node:".length()).trim();
            if (id.isEmpty()) {
                throw new DriverException("Wi-SUN lab node mapping requires id: " + mapping);
            }
            return new WisunPoint("/nodes/" + id + "/value");
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return new WisunPoint(normalized);
    }
}
