package com.ispf.driver.omronfins;

import java.util.Locale;
import java.util.Map;

/**
 * Point mapping: {@code memoryArea:address:count} e.g. {@code DM:100:1}.
 */
public record OmronFinsPoint(String memoryArea, int address, int count) {

    private static final Map<String, Byte> MEMORY_AREA_CODES = Map.of(
            "CIO", (byte) 0x30,
            "WR", (byte) 0x31,
            "HR", (byte) 0x32,
            "AR", (byte) 0x33,
            "DM", (byte) 0x82
    );

    public static OmronFinsPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Omron FINS point mapping is blank");
        }
        String[] parts = raw.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Omron FINS point must be memoryArea:address:count");
        }
        String area = parts[0].trim().toUpperCase(Locale.ROOT);
        if (!MEMORY_AREA_CODES.containsKey(area)) {
            throw new IllegalArgumentException("Unknown Omron FINS memory area: " + area);
        }
        return new OmronFinsPoint(
                area,
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
        );
    }

    public byte memoryAreaCode() {
        return MEMORY_AREA_CODES.get(memoryArea);
    }
}
