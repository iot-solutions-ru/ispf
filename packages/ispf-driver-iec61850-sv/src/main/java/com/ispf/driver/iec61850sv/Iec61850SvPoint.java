package com.ispf.driver.iec61850sv;

import com.ispf.driver.DriverException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IEC 61850 Sampled Values lab point.
 * <p>
 * Forms: {@code svID:MU1}, {@code sv:1:smp:0}.
 */
record Iec61850SvPoint(Kind kind, String svId, int appId, int sampleIndex) {

    enum Kind {
        SV_ID,
        SAMPLE
    }

    private static final Pattern SV_ID = Pattern.compile(
            "^svID\\s*[:=]\\s*([A-Za-z0-9_\\-]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAMPLE = Pattern.compile(
            "^sv\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*smp\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    static Iec61850SvPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("IEC 61850 SV-lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher id = SV_ID.matcher(trimmed);
        if (id.matches()) {
            return new Iec61850SvPoint(Kind.SV_ID, id.group(1), 0, 0);
        }
        Matcher sample = SAMPLE.matcher(trimmed);
        if (sample.matches()) {
            int appId = Integer.parseInt(sample.group(1));
            int smp = Integer.parseInt(sample.group(2));
            if (appId < 0 || appId > 0xFFFF) {
                throw new DriverException("IEC 61850 SV-lab appId out of range: " + appId);
            }
            if (smp < 0 || smp > 0xFFFF) {
                throw new DriverException("IEC 61850 SV-lab sample index out of range: " + smp);
            }
            return new Iec61850SvPoint(Kind.SAMPLE, null, appId, smp);
        }
        throw new DriverException(
                "Unsupported IEC 61850 SV-lab mapping"
                        + " (expected svID:MU1 or sv:1:smp:0): " + mapping);
    }

    String wireToken() {
        return switch (kind) {
            case SV_ID -> "svID:" + svId;
            case SAMPLE -> "sv:" + appId + ":smp:" + sampleIndex;
        };
    }

    String display() {
        return wireToken();
    }

    String kindName() {
        return kind == Kind.SV_ID ? "svID" : "sample";
    }
}
