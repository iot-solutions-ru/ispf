package com.ispf.driver.profinet;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PROFINET IO gateway lab point.
 * <p>
 * Forms: {@code slot:1:subslot:1}, {@code device:1:api:0:slot:1}.
 */
record ProfinetPoint(Kind kind, int device, int api, int slot, int subslot) {

    enum Kind {
        SLOT_SUBSLOT,
        DEVICE_API_SLOT
    }

    private static final Pattern SLOT_SUBSLOT = Pattern.compile(
            "^slot\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*subslot\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICE_API_SLOT = Pattern.compile(
            "^device\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*api\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*slot\\s*[:=]\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    static ProfinetPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("PROFINET lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher slotSub = SLOT_SUBSLOT.matcher(trimmed);
        if (slotSub.matches()) {
            int slot = Integer.parseInt(slotSub.group(1));
            int subslot = Integer.parseInt(slotSub.group(2));
            return create(Kind.SLOT_SUBSLOT, 0, 0, slot, subslot);
        }
        Matcher deviceApi = DEVICE_API_SLOT.matcher(trimmed);
        if (deviceApi.matches()) {
            int device = Integer.parseInt(deviceApi.group(1));
            int api = Integer.parseInt(deviceApi.group(2));
            int slot = Integer.parseInt(deviceApi.group(3));
            return create(Kind.DEVICE_API_SLOT, device, api, slot, 0);
        }
        throw new DriverException(
                "Unsupported PROFINET lab mapping"
                        + " (expected slot:1:subslot:1 or device:1:api:0:slot:1): " + mapping);
    }

    private static ProfinetPoint create(Kind kind, int device, int api, int slot, int subslot)
            throws DriverException {
        if (device < 0 || device > 255) {
            throw new DriverException("PROFINET lab device out of range: " + device);
        }
        if (api < 0 || api > 65535) {
            throw new DriverException("PROFINET lab api out of range: " + api);
        }
        if (slot < 0 || slot > 255) {
            throw new DriverException("PROFINET lab slot out of range: " + slot);
        }
        if (subslot < 0 || subslot > 255) {
            throw new DriverException("PROFINET lab subslot out of range: " + subslot);
        }
        return new ProfinetPoint(kind, device, api, slot, subslot);
    }

    String wireToken() {
        return switch (kind) {
            case SLOT_SUBSLOT -> "slot:" + slot + ":subslot:" + subslot;
            case DEVICE_API_SLOT -> "device:" + device + ":api:" + api + ":slot:" + slot;
        };
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }

    String kindName() {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
