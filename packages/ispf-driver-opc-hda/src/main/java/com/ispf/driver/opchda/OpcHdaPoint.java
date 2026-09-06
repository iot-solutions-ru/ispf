package com.ispf.driver.opchda;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OPC HDA HTTP/JSON gateway lab point.
 * <p>
 * Forms: {@code item:Tag1}, {@code tag:Temperature}.
 */
record OpcHdaPoint(Kind kind, String name) {

    enum Kind {
        ITEM,
        TAG
    }

    private static final Pattern MAPPING = Pattern.compile(
            "^(item|tag)\\s*[:=]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    static OpcHdaPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("OPC HDA gateway point mapping is blank");
        }
        Matcher matcher = MAPPING.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported OPC HDA mapping (expected item:Tag1 or tag:Temperature): "
                            + mapping);
        }
        String kindToken = matcher.group(1).toLowerCase(Locale.ROOT);
        String name = matcher.group(2).trim();
        if (name.isEmpty()) {
            throw new DriverException("OPC HDA gateway point mapping missing name: " + mapping);
        }
        Kind kind = "tag".equals(kindToken) ? Kind.TAG : Kind.ITEM;
        return new OpcHdaPoint(kind, name);
    }

    String kindToken() {
        return kind == Kind.TAG ? "tag" : "item";
    }

    String display() {
        return kindToken() + ":" + name;
    }
}
