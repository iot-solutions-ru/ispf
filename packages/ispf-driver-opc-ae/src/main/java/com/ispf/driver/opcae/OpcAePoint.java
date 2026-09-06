package com.ispf.driver.opcae;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OPC A&amp;E HTTP/JSON gateway lab point.
 * <p>
 * Forms: {@code alarm:1}, {@code source:Tank1}, {@code area:Plant}.
 */
record OpcAePoint(Kind kind, String id) {

    enum Kind {
        ALARM,
        SOURCE,
        AREA
    }

    private static final Pattern MAPPING = Pattern.compile(
            "^(alarm|source|area)\\s*[:=]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    static OpcAePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("OPC A&E gateway point mapping is blank");
        }
        Matcher matcher = MAPPING.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported OPC A&E mapping (expected alarm:1, source:Tank1, area:Plant): "
                            + mapping);
        }
        String kindToken = matcher.group(1).toLowerCase(Locale.ROOT);
        String id = matcher.group(2).trim();
        if (id.isEmpty()) {
            throw new DriverException("OPC A&E gateway point mapping missing id: " + mapping);
        }
        Kind kind = switch (kindToken) {
            case "alarm" -> Kind.ALARM;
            case "source" -> Kind.SOURCE;
            case "area" -> Kind.AREA;
            default -> throw new DriverException("Unsupported OPC A&E kind: " + kindToken);
        };
        return new OpcAePoint(kind, id);
    }

    String kindToken() {
        return switch (kind) {
            case ALARM -> "alarm";
            case SOURCE -> "source";
            case AREA -> "area";
        };
    }

    String display() {
        return kindToken() + ":" + id;
    }

    boolean acknowledgeable() {
        return kind == Kind.ALARM;
    }
}
