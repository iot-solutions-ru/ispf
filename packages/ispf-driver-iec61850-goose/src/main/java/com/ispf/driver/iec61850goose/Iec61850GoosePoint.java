package com.ispf.driver.iec61850goose;

import com.ispf.driver.DriverException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IEC 61850 GOOSE-lab point.
 * <p>
 * Forms: {@code goose:gcb1}, {@code goID:MyGo}.
 */
record Iec61850GoosePoint(Kind kind, String name) {

    enum Kind {
        GOOSE,
        GO_ID
    }

    private static final Pattern GOOSE = Pattern.compile(
            "^goose\\s*[:=]\\s*([A-Za-z0-9_\\-]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GO_ID = Pattern.compile(
            "^goID\\s*[:=]\\s*([A-Za-z0-9_\\-]+)$", Pattern.CASE_INSENSITIVE);

    static Iec61850GoosePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("IEC 61850 GOOSE-lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher goose = GOOSE.matcher(trimmed);
        if (goose.matches()) {
            return new Iec61850GoosePoint(Kind.GOOSE, goose.group(1));
        }
        Matcher goId = GO_ID.matcher(trimmed);
        if (goId.matches()) {
            return new Iec61850GoosePoint(Kind.GO_ID, goId.group(1));
        }
        throw new DriverException(
                "Unsupported IEC 61850 GOOSE-lab mapping"
                        + " (expected goose:gcb1 or goID:MyGo): " + mapping);
    }

    String wireToken() {
        return switch (kind) {
            case GOOSE -> "goose:" + name;
            case GO_ID -> "goID:" + name;
        };
    }

    String display() {
        return wireToken();
    }

    String kindName() {
        return kind == Kind.GO_ID ? "goID" : "goose";
    }
}
