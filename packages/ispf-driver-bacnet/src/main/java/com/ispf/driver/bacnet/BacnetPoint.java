package com.ispf.driver.bacnet;

import com.ispf.driver.DriverException;
import com.ispf.driver.bacnet.codec.BacnetObjectType;
import com.ispf.driver.bacnet.codec.BacnetPropertyIdentifier;

/**
 * Parsed BACnet point reference from mapping string {@code objectType:instance:property}.
 * Example: {@code analog-input:1:present-value}.
 */
record BacnetPoint(BacnetObjectType objectType, int instance, BacnetPropertyIdentifier property) {

    static BacnetPoint parse(String mapping) throws DriverException {
        String[] parts = mapping.split(":");
        if (parts.length < 3) {
            throw new DriverException("Invalid BACnet mapping (expected objectType:instance:property): " + mapping);
        }
        try {
            BacnetObjectType objectType = parseObjectType(parts[0].trim());
            int instance = Integer.parseInt(parts[1].trim());
            BacnetPropertyIdentifier property = parseProperty(parts[2].trim());
            return new BacnetPoint(objectType, instance, property);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Invalid BACnet mapping: " + mapping, e);
        }
    }

    private static BacnetObjectType parseObjectType(String name) {
        String normalized = name.trim().toLowerCase().replace('_', '-');
        return switch (normalized) {
            case "analog-input", "ai" -> BacnetObjectType.ANALOG_INPUT;
            case "analog-value", "av" -> BacnetObjectType.ANALOG_VALUE;
            case "binary-input", "bi" -> BacnetObjectType.BINARY_INPUT;
            case "binary-value", "bv" -> BacnetObjectType.BINARY_VALUE;
            default -> BacnetObjectType.fromName(normalized);
        };
    }

    private static BacnetPropertyIdentifier parseProperty(String name) {
        String normalized = name.trim().toLowerCase().replace('_', '-');
        return switch (normalized) {
            case "present-value", "value" -> BacnetPropertyIdentifier.PRESENT_VALUE;
            case "units" -> BacnetPropertyIdentifier.UNITS;
            default -> BacnetPropertyIdentifier.fromName(normalized);
        };
    }
}
