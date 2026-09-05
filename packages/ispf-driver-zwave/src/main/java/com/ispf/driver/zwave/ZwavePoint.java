package com.ispf.driver.zwave;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Z-Wave controller gateway lab point.
 * <p>
 * Forms: {@code node:3}, {@code node:3:cmd:37}.
 */
record ZwavePoint(Kind kind, String display, int node, int commandClass) {

    enum Kind {
        NODE,
        CMD
    }

    private static final Pattern NODE = Pattern.compile(
            "^node\\s*[:=]\\s*(\\d+)(?:\\s*[:=]\\s*cmd\\s*[:=]\\s*(0x[0-9A-Fa-f]+|\\d+))?$",
            Pattern.CASE_INSENSITIVE);

    static ZwavePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Z-Wave point mapping is blank");
        }
        Matcher matcher = NODE.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported Z-Wave mapping (expected node:3 or node:3:cmd:37): " + mapping);
        }
        int node = Integer.parseInt(matcher.group(1));
        if (node < 1 || node > 232) {
            throw new DriverException("Z-Wave node out of range: " + node);
        }
        if (matcher.group(2) == null) {
            return new ZwavePoint(Kind.NODE, "node:" + node, node, -1);
        }
        int cmd = parseIntToken(matcher.group(2));
        String display = "node:" + node + ":cmd:" + cmd;
        return new ZwavePoint(Kind.CMD, display.toLowerCase(Locale.ROOT), node, cmd);
    }

    private static int parseIntToken(String token) {
        String t = token.trim();
        if (t.regionMatches(true, 0, "0x", 0, 2)) {
            return Integer.parseInt(t.substring(2), 16);
        }
        return Integer.parseInt(t);
    }

    boolean writable() {
        return true;
    }

    String wireToken() {
        return display;
    }
}
