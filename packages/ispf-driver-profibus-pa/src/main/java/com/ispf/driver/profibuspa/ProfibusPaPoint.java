package com.ispf.driver.profibuspa;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PROFIBUS PA instrument gateway lab point.
 * <p>
 * Forms: {@code slot:1}, {@code slot:1:pv}, {@code addr:12}, {@code pa:1}.
 */
record ProfibusPaPoint(String wireToken, String kind, int index) {

    private static final Pattern SLOT_PV = Pattern.compile(
            "^slot\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*pv$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT = Pattern.compile(
            "^slot\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDR = Pattern.compile(
            "^addr\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PA = Pattern.compile(
            "^pa\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    static ProfibusPaPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("PROFIBUS PA point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher slotPv = SLOT_PV.matcher(trimmed);
        if (slotPv.matches()) {
            int slot = Integer.parseInt(slotPv.group(1));
            validateIndex("slot", slot);
            return new ProfibusPaPoint("slot:" + slot + ":pv", "slot-pv", slot);
        }
        Matcher slot = SLOT.matcher(trimmed);
        if (slot.matches()) {
            return create("slot", Integer.parseInt(slot.group(1)));
        }
        Matcher addr = ADDR.matcher(trimmed);
        if (addr.matches()) {
            return create("addr", Integer.parseInt(addr.group(1)));
        }
        Matcher pa = PA.matcher(trimmed);
        if (pa.matches()) {
            return create("pa", Integer.parseInt(pa.group(1)));
        }
        throw new DriverException(
                "Unsupported PROFIBUS PA mapping"
                        + " (expected slot:1, slot:1:pv, addr:12, or pa:1): " + mapping);
    }

    private static ProfibusPaPoint create(String kind, int index) throws DriverException {
        validateIndex(kind, index);
        return new ProfibusPaPoint(kind + ":" + index, kind, index);
    }

    private static void validateIndex(String kind, int index) throws DriverException {
        if (index < 0 || index > 65535) {
            throw new DriverException("PROFIBUS PA " + kind + " out of range: " + index);
        }
    }

    String display() {
        return wireToken.toLowerCase(Locale.ROOT);
    }
}
