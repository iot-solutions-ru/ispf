package com.ispf.driver.profibus;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PROFIBUS DP gateway lab point.
 * <p>
 * Forms: {@code slave:3}, {@code slave:3:byte:0}.
 */
record ProfibusPoint(int slave, int byteOffset) {

    private static final Pattern SLAVE_BYTE = Pattern.compile(
            "^slave\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*byte\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SLAVE_ONLY = Pattern.compile(
            "^slave\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    static ProfibusPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("PROFIBUS lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher slaveByte = SLAVE_BYTE.matcher(trimmed);
        if (slaveByte.matches()) {
            return create(Integer.parseInt(slaveByte.group(1)), Integer.parseInt(slaveByte.group(2)));
        }
        Matcher slaveOnly = SLAVE_ONLY.matcher(trimmed);
        if (slaveOnly.matches()) {
            return create(Integer.parseInt(slaveOnly.group(1)), 0);
        }
        throw new DriverException(
                "Unsupported PROFIBUS lab mapping (expected slave:3 or slave:3:byte:0): " + mapping);
    }

    private static ProfibusPoint create(int slave, int byteOffset) throws DriverException {
        if (slave < 0 || slave > 126) {
            throw new DriverException("PROFIBUS lab slave out of range: " + slave);
        }
        if (byteOffset < 0 || byteOffset > 255) {
            throw new DriverException("PROFIBUS lab byte offset out of range: " + byteOffset);
        }
        return new ProfibusPoint(slave, byteOffset);
    }

    String wireToken() {
        return "slave:" + slave + ":byte:" + byteOffset;
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }
}
