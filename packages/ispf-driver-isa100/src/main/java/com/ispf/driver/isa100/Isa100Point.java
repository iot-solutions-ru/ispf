package com.ispf.driver.isa100;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Parsed ISA100 gateway lab point (ASCII/JSON path or tag).
 * <p>
 * Accepted forms: {@code pv}, {@code tag:FI-101}, {@code device:1/pv}, {@code /devices/1/pv}.
 */
record Isa100Point(String path) {

    static Isa100Point parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("ISA100 lab point mapping is blank");
        }
        String normalized = mapping.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("pv".equals(lower) || "primary".equals(lower)) {
            return new Isa100Point("/devices/1/pv");
        }
        if (lower.startsWith("tag:")) {
            String tag = normalized.substring(4).trim();
            if (tag.isEmpty()) {
                throw new DriverException("ISA100 lab tag mapping requires name: " + mapping);
            }
            return new Isa100Point("/tags/" + tag);
        }
        if (lower.startsWith("device:")) {
            String rest = normalized.substring(7).trim();
            int slash = rest.indexOf('/');
            if (slash < 0) {
                return new Isa100Point("/devices/" + rest + "/pv");
            }
            String device = rest.substring(0, slash).trim();
            String attr = rest.substring(slash + 1).trim();
            return new Isa100Point("/devices/" + device + "/" + attr);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return new Isa100Point(normalized);
    }
}
