package com.ispf.driver.bacnet.codec;

/**
 * Application values exchanged by the supported BACnet services.
 */
public sealed interface BacnetValue permits BacnetValue.RealValue, BacnetValue.BinaryValue, BacnetValue.UnsignedValue {

    record RealValue(float value) implements BacnetValue {
    }

    record BinaryValue(boolean active) implements BacnetValue {
    }

    record UnsignedValue(int value) implements BacnetValue {
        public UnsignedValue {
            if (value < 0) {
                throw new IllegalArgumentException("BACnet unsigned value must be non-negative");
            }
        }
    }
}
