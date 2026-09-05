package com.ispf.driver.zigbee;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zigbee ZCL coordinator gateway lab point.
 * <p>
 * Forms: {@code nwk:0x1234:ep:1:cluster:0x0402:attr:0}, {@code ieee:00124b0001234567}.
 */
record ZigbeePoint(Kind kind, String display, int nwk, int endpoint, int cluster, int attr, String ieee) {

    enum Kind {
        ZCL_ATTR,
        IEEE
    }

    private static final Pattern ZCL = Pattern.compile(
            "^nwk\\s*[:=]\\s*(0x[0-9A-Fa-f]+|\\d+)"
                    + "\\s*[:=]\\s*ep\\s*[:=]\\s*(\\d+)"
                    + "\\s*[:=]\\s*cluster\\s*[:=]\\s*(0x[0-9A-Fa-f]+|\\d+)"
                    + "\\s*[:=]\\s*attr\\s*[:=]\\s*(0x[0-9A-Fa-f]+|\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IEEE = Pattern.compile(
            "^ieee\\s*[:=]\\s*([0-9A-Fa-f]{16})$",
            Pattern.CASE_INSENSITIVE);

    static ZigbeePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Zigbee point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher zcl = ZCL.matcher(trimmed);
        if (zcl.matches()) {
            int nwk = parseIntToken(zcl.group(1));
            int ep = Integer.parseInt(zcl.group(2));
            int cluster = parseIntToken(zcl.group(3));
            int attr = parseIntToken(zcl.group(4));
            if (ep < 1 || ep > 255) {
                throw new DriverException("Zigbee endpoint out of range: " + ep);
            }
            String display = String.format(Locale.ROOT,
                    "nwk:0x%04x:ep:%d:cluster:0x%04x:attr:%d",
                    nwk, ep, cluster, attr);
            return new ZigbeePoint(Kind.ZCL_ATTR, display, nwk, ep, cluster, attr, null);
        }
        Matcher ieee = IEEE.matcher(trimmed);
        if (ieee.matches()) {
            String addr = ieee.group(1).toLowerCase(Locale.ROOT);
            return new ZigbeePoint(Kind.IEEE, "ieee:" + addr, -1, -1, -1, -1, addr);
        }
        throw new DriverException(
                "Unsupported Zigbee mapping (expected nwk:0x1234:ep:1:cluster:0x0402:attr:0"
                        + " or ieee:00124b0001234567): " + mapping);
    }

    private static int parseIntToken(String token) {
        String t = token.trim();
        if (t.regionMatches(true, 0, "0x", 0, 2)) {
            return Integer.parseInt(t.substring(2), 16);
        }
        return Integer.parseInt(t);
    }

    boolean writable() {
        return kind == Kind.ZCL_ATTR;
    }

    String wireToken() {
        return display;
    }
}
