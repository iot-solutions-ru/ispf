package com.ispf.driver.bacnet;

import com.ispf.driver.bacnet.codec.BacnetEngineeringUnit;

/**
 * Maps BACnet engineering units to Haystack-friendly unit strings (BL-81).
 */
final class BacnetEngineeringUnits {

    private BacnetEngineeringUnits() {
    }

    static String toHaystackUnit(BacnetEngineeringUnit unit) {
        if (unit == null) {
            return "";
        }
        return unit.haystackUnit();
    }
}
