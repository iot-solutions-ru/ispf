package com.ispf.driver.bacnet;

import com.ispf.driver.bacnet.codec.BacnetObjectType;
import com.ispf.driver.bacnet.codec.BacnetValue;

/**
 * Formats BACnet property values for ISPF variable payloads (BL-81).
 */
final class BacnetValueDecoder {

    private BacnetValueDecoder() {
    }

    static String formatValue(BacnetValue rawValue, BacnetObjectType objectType) {
        if (rawValue == null) {
            return "";
        }
        if (rawValue instanceof BacnetValue.RealValue real) {
            return trimFloat(real.value());
        }
        if (rawValue instanceof BacnetValue.BinaryValue binary) {
            return binary.active() ? "active" : "inactive";
        }
        if (rawValue instanceof BacnetValue.UnsignedValue state) {
            return Integer.toString(state.value());
        }
        return rawValue.toString();
    }

    static boolean supportsUnitMetadata(BacnetObjectType objectType) {
        return objectType.isAnalog();
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
