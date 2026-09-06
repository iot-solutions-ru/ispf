package com.ispf.driver.cclink;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CC-Link SLMP/ASCII gateway lab point.
 * <p>
 * Forms: {@code D100}, {@code R0}, {@code W0}, {@code dev:D100}.
 */
record CcLinkPoint(String deviceCode, int address) {

    private static final Pattern DEV_PREFIX = Pattern.compile(
            "^dev\\s*[:=]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICE = Pattern.compile(
            "^([DRWdrw])\\s*[:=]?\\s*(\\d+)$");

    static CcLinkPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("CC-Link lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher pref = DEV_PREFIX.matcher(trimmed);
        if (pref.matches()) {
            trimmed = pref.group(1).trim();
        }
        Matcher device = DEVICE.matcher(trimmed);
        if (!device.matches()) {
            throw new DriverException(
                    "Unsupported CC-Link lab mapping (expected D100, R0, W0, or dev:D100): "
                            + mapping);
        }
        String code = device.group(1).toUpperCase(Locale.ROOT);
        int address = Integer.parseInt(device.group(2));
        if (address < 0 || address > 65535) {
            throw new DriverException("CC-Link lab address out of range: " + address);
        }
        return new CcLinkPoint(code, address);
    }

    String wireToken() {
        return deviceCode + address;
    }

    String display() {
        return wireToken();
    }

    String kind() {
        return deviceCode;
    }
}
