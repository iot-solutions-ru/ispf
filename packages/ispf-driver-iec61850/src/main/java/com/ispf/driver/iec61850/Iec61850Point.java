package com.ispf.driver.iec61850;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IEC 61850 MMS-lab point (object-reference shaped).
 * <p>
 * Forms: {@code LD0/MMXU1.TotW.mag.f}, {@code IED1/LLN0.Mod.stVal}.
 */
record Iec61850Point(String reference) {

    private static final Pattern REF = Pattern.compile(
            "^([A-Za-z0-9_]+)/([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)$");

    static Iec61850Point parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("IEC 61850 MMS-lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher matcher = REF.matcher(trimmed);
        if (!matcher.matches()) {
            throw new DriverException(
                    "Unsupported IEC 61850 MMS-lab mapping"
                            + " (expected LD0/MMXU1.TotW.mag.f or IED1/LLN0.Mod.stVal): "
                            + mapping);
        }
        return new Iec61850Point(trimmed);
    }

    String wireToken() {
        return reference;
    }

    String display() {
        return reference;
    }

    String kind() {
        String lower = reference.toLowerCase(Locale.ROOT);
        if (lower.contains(".mag.") || lower.contains(".mag")) {
            return "analog";
        }
        if (lower.contains(".stval") || lower.contains(".mod.")) {
            return "status";
        }
        return "mms";
    }
}
