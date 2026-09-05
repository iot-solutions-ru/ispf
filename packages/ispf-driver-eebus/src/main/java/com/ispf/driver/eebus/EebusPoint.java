package com.ispf.driver.eebus;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * Parsed EEBus SHIP/SPINE-lite TCP lab point.
 * <p>
 * Accepted forms: {@code power}, {@code setpoint},
 * {@code entity:ElectricalConnection:power}, {@code entity:ElectricalConnection:setpoint}.
 */
record EebusPoint(String entity, String path, boolean entityForm) {

    private static final String DEFAULT_ENTITY = "ElectricalConnection";

    static EebusPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("EEBus lab point mapping is blank");
        }
        String normalized = mapping.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("entity:")) {
                String rest = normalized.substring("entity:".length()).trim();
                int colon = rest.indexOf(':');
                if (colon <= 0 || colon == rest.length() - 1) {
                    throw new DriverException(
                            "EEBus lab entity mapping requires entity:Name:path: " + mapping);
                }
                String entity = rest.substring(0, colon).trim();
                String path = normalizePath(rest.substring(colon + 1).trim());
                if (entity.isEmpty() || path.isEmpty()) {
                    throw new DriverException("EEBus lab entity mapping incomplete: " + mapping);
                }
                return new EebusPoint(entity, path, true);
            }
            if ("power".equals(lower) || "setpoint".equals(lower)
                    || "powerconsumption".equals(lower)) {
                String path = "powerconsumption".equals(lower) ? "power" : lower;
                return new EebusPoint(DEFAULT_ENTITY, path, false);
            }
            return new EebusPoint(DEFAULT_ENTITY, normalizePath(normalized), false);
        } catch (DriverException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DriverException("Invalid EEBus lab point mapping: " + mapping, e);
        }
    }

    private static String normalizePath(String path) {
        if ("PowerConsumption".equalsIgnoreCase(path) || "power".equalsIgnoreCase(path)) {
            return "power";
        }
        if ("Setpoint".equalsIgnoreCase(path) || "setpoint".equalsIgnoreCase(path)) {
            return "setpoint";
        }
        return path;
    }

    /** Canonical ASCII token exchanged with the lab (e.g. {@code power}, {@code entity:…:power}). */
    String gatewayToken() {
        if (entityForm) {
            return "entity:" + entity + ":" + path;
        }
        return path.toLowerCase(Locale.ROOT);
    }

    boolean writable() {
        return "setpoint".equalsIgnoreCase(path);
    }
}
