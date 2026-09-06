package com.ispf.driver.foundationfieldbus;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Foundation Fieldbus HSE/TCP gateway lab point.
 * <p>
 * Forms: {@code ai:1}, {@code ao:2}, {@code device:0:pv}, {@code ff:1}.
 */
record FoundationFieldbusPoint(String wireToken, String kind, int index) {

    private static final Pattern AI = Pattern.compile(
            "^ai\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AO = Pattern.compile(
            "^ao\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICE_PV = Pattern.compile(
            "^device\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*pv$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FF = Pattern.compile(
            "^ff\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    static FoundationFieldbusPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Foundation Fieldbus point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher ai = AI.matcher(trimmed);
        if (ai.matches()) {
            return create("ai", Integer.parseInt(ai.group(1)));
        }
        Matcher ao = AO.matcher(trimmed);
        if (ao.matches()) {
            return create("ao", Integer.parseInt(ao.group(1)));
        }
        Matcher devicePv = DEVICE_PV.matcher(trimmed);
        if (devicePv.matches()) {
            int device = Integer.parseInt(devicePv.group(1));
            if (device < 0 || device > 65535) {
                throw new DriverException("Foundation Fieldbus device out of range: " + device);
            }
            return new FoundationFieldbusPoint("device:" + device + ":pv", "device-pv", device);
        }
        Matcher ff = FF.matcher(trimmed);
        if (ff.matches()) {
            return create("ff", Integer.parseInt(ff.group(1)));
        }
        throw new DriverException(
                "Unsupported Foundation Fieldbus mapping"
                        + " (expected ai:1, ao:2, device:0:pv, or ff:1): " + mapping);
    }

    private static FoundationFieldbusPoint create(String kind, int index) throws DriverException {
        if (index < 0 || index > 65535) {
            throw new DriverException("Foundation Fieldbus " + kind + " index out of range: " + index);
        }
        return new FoundationFieldbusPoint(kind + ":" + index, kind, index);
    }

    String display() {
        return wireToken.toLowerCase(Locale.ROOT);
    }
}
