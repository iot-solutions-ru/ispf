package com.ispf.driver.bacnetmstp;

import com.ispf.driver.DriverException;

import java.util.Locale;

/**
 * BACnet MS/TP gateway lab point: object + instance (+ optional property).
 * <p>
 * Forms: {@code analog-input,1}, {@code AI:1}, {@code AO:2}, {@code AV:3},
 * {@code analog-value:1:present-value}.
 */
record BacnetMstpPoint(ObjectType objectType, int instance, int propertyId) {

    static final int PRESENT_VALUE = 85;

    enum ObjectType {
        ANALOG_INPUT(0, true, false),
        ANALOG_OUTPUT(1, true, true),
        ANALOG_VALUE(2, true, true);

        final int id;
        final boolean analog;
        final boolean writable;

        ObjectType(int id, boolean analog, boolean writable) {
            this.id = id;
            this.analog = analog;
            this.writable = writable;
        }
    }

    static BacnetMstpPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("BACnet MS/TP point mapping is blank");
        }
        String normalized = mapping.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        try {
            String[] comma = normalized.split(",");
            if (comma.length == 2) {
                return new BacnetMstpPoint(parseType(comma[0].trim()), Integer.parseInt(comma[1].trim()), PRESENT_VALUE);
            }
            String[] parts = normalized.split(":");
            if (parts.length == 2) {
                return new BacnetMstpPoint(parseType(parts[0].trim()), Integer.parseInt(parts[1].trim()), PRESENT_VALUE);
            }
            if (parts.length >= 3) {
                int property = "present-value".equals(parts[2].trim()) || "value".equals(parts[2].trim())
                        ? PRESENT_VALUE
                        : Integer.parseInt(parts[2].trim());
                return new BacnetMstpPoint(parseType(parts[0].trim()), Integer.parseInt(parts[1].trim()), property);
            }
            throw new DriverException("Unsupported BACnet MS/TP mapping: " + mapping);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Invalid BACnet MS/TP mapping: " + mapping, e);
        }
    }

    private static ObjectType parseType(String name) {
        return switch (name) {
            case "analog-input", "ai" -> ObjectType.ANALOG_INPUT;
            case "analog-output", "ao" -> ObjectType.ANALOG_OUTPUT;
            case "analog-value", "av" -> ObjectType.ANALOG_VALUE;
            default -> throw new IllegalArgumentException(name);
        };
    }

    int encodedObjectId() {
        return ((objectType.id & 0x3FF) << 22) | (instance & 0x3FFFFF);
    }
}
