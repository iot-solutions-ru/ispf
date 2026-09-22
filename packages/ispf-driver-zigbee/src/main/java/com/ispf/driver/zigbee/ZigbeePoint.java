package com.ispf.driver.zigbee;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * ASH (EZSP UART) point — RSTACK version or reason only (not ZCL attributes).
 * <p>
 * Forms: {@code version}, {@code reason}, {@code rstack:version}, {@code rstack:reason}.
 */
record ZigbeePoint(Kind kind, String display) {

    enum Kind {
        VERSION,
        REASON
    }

    static ZigbeePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Zigbee ASH point mapping is blank");
        }
        String trimmed = mapping.trim().toLowerCase(Locale.ROOT);
        if ("version".equals(trimmed) || "rstack:version".equals(trimmed)) {
            return new ZigbeePoint(Kind.VERSION, "rstack:version");
        }
        if ("reason".equals(trimmed) || "rstack:reason".equals(trimmed)) {
            return new ZigbeePoint(Kind.REASON, "rstack:reason");
        }
        throw new DriverException(
                "Unsupported Zigbee ASH mapping (expected version|reason|rstack:version|rstack:reason): "
                        + mapping);
    }
}
