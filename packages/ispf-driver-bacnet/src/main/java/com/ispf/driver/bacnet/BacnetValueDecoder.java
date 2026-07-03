package com.ispf.driver.bacnet;

import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

/**
 * Formats BACnet property values for ISPF variable payloads (BL-81).
 */
final class BacnetValueDecoder {

    private BacnetValueDecoder() {
    }

    static String formatValue(Encodable rawValue, ObjectType objectType) {
        if (rawValue == null) {
            return "";
        }
        if (rawValue instanceof Real real) {
            return trimFloat(real.floatValue());
        }
        if (rawValue instanceof BinaryPV binary) {
            return binary.equals(BinaryPV.active) ? "active" : "inactive";
        }
        if (rawValue instanceof UnsignedInteger state) {
            return Integer.toString(state.intValue());
        }
        return rawValue.toString();
    }

    static boolean supportsUnitMetadata(ObjectType objectType) {
        return isAnalogType(objectType);
    }

    private static boolean isAnalogType(ObjectType objectType) {
        return objectType.equals(ObjectType.analogInput)
                || objectType.equals(ObjectType.analogOutput)
                || objectType.equals(ObjectType.analogValue);
    }

    private static String trimFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return Float.toString(value);
        }
        if (value == Math.rint(value)) {
            return Integer.toString(Math.round(value));
        }
        return Float.toString(value);
    }
}
