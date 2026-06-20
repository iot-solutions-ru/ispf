package com.ispf.driver.bacnet;

import com.ispf.driver.DriverException;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

/**
 * Parsed BACnet point reference from mapping string {@code objectType:instance:property}.
 * Example: {@code analog-input:1:present-value}.
 */
record BacnetPoint(ObjectType objectType, int instance, PropertyIdentifier property) {

    static BacnetPoint parse(String mapping) throws DriverException {
        String[] parts = mapping.split(":");
        if (parts.length < 3) {
            throw new DriverException("Invalid BACnet mapping (expected objectType:instance:property): " + mapping);
        }
        try {
            ObjectType objectType = parseObjectType(parts[0].trim());
            int instance = Integer.parseInt(parts[1].trim());
            PropertyIdentifier property = parseProperty(parts[2].trim());
            return new BacnetPoint(objectType, instance, property);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Invalid BACnet mapping: " + mapping, e);
        }
    }

    private static ObjectType parseObjectType(String name) {
        String normalized = name.trim().toLowerCase().replace('_', '-');
        return switch (normalized) {
            case "analog-input", "ai" -> ObjectType.analogInput;
            case "analog-output", "ao" -> ObjectType.analogOutput;
            case "analog-value", "av" -> ObjectType.analogValue;
            case "binary-input", "bi" -> ObjectType.binaryInput;
            case "binary-output", "bo" -> ObjectType.binaryOutput;
            case "binary-value", "bv" -> ObjectType.binaryValue;
            case "multi-state-input", "mi" -> ObjectType.multiStateInput;
            case "multi-state-output", "mo" -> ObjectType.multiStateOutput;
            case "multi-state-value", "mv" -> ObjectType.multiStateValue;
            default -> ObjectType.forName(normalized);
        };
    }

    private static PropertyIdentifier parseProperty(String name) {
        String normalized = name.trim().toLowerCase().replace('_', '-');
        return switch (normalized) {
            case "present-value", "value" -> PropertyIdentifier.presentValue;
            case "status-flags" -> PropertyIdentifier.statusFlags;
            case "description" -> PropertyIdentifier.description;
            case "object-name" -> PropertyIdentifier.objectName;
            default -> PropertyIdentifier.forName(normalized);
        };
    }
}
