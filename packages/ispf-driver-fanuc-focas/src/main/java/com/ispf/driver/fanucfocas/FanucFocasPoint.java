package com.ispf.driver.fanucfocas;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fanuc FOCAS-shaped TCP gateway lab point.
 * <p>
 * Forms: {@code pmc:D0001}, {@code cnc:abs:1}, {@code stat:run}.
 */
record FanucFocasPoint(Kind kind, String display, String pmcAddress, int axis) {

    enum Kind {
        PMC,
        CNC_ABS,
        STAT_RUN
    }

    private static final Pattern PMC = Pattern.compile(
            "^pmc\\s*[:=]\\s*([A-Za-z]\\d{1,6})$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CNC_ABS = Pattern.compile(
            "^cnc\\s*[:=]\\s*abs\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STAT_RUN = Pattern.compile(
            "^stat\\s*[:=]\\s*run$",
            Pattern.CASE_INSENSITIVE);

    static FanucFocasPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Fanuc FOCAS point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher pmc = PMC.matcher(trimmed);
        if (pmc.matches()) {
            String address = pmc.group(1).toUpperCase(Locale.ROOT);
            return new FanucFocasPoint(Kind.PMC, "pmc:" + address, address, -1);
        }
        Matcher abs = CNC_ABS.matcher(trimmed);
        if (abs.matches()) {
            int axis = Integer.parseInt(abs.group(1));
            if (axis < 1 || axis > 32) {
                throw new DriverException("Fanuc FOCAS CNC axis out of range: " + axis);
            }
            return new FanucFocasPoint(Kind.CNC_ABS, "cnc:abs:" + axis, null, axis);
        }
        Matcher run = STAT_RUN.matcher(trimmed);
        if (run.matches()) {
            return new FanucFocasPoint(Kind.STAT_RUN, "stat:run", null, -1);
        }
        throw new DriverException(
                "Unsupported Fanuc FOCAS mapping (expected pmc:D0001, cnc:abs:1, or stat:run): "
                        + mapping);
    }

    boolean writable() {
        return kind == Kind.PMC;
    }

    String wireToken() {
        return display;
    }

    String kindToken() {
        return switch (kind) {
            case PMC -> "pmc";
            case CNC_ABS -> "cnc-abs";
            case STAT_RUN -> "stat-run";
        };
    }
}
