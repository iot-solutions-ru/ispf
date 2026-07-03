package com.ispf.driver.bacnet;

import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;

/**
 * Maps BACnet engineering units to Haystack-friendly unit strings (BL-81).
 */
final class BacnetEngineeringUnits {

    private BacnetEngineeringUnits() {
    }

    static String toHaystackUnit(EngineeringUnits unit) {
        if (unit == null || unit.equals(EngineeringUnits.noUnits)) {
            return "";
        }
        if (unit.equals(EngineeringUnits.degreesCelsius)) {
            return "°C";
        }
        if (unit.equals(EngineeringUnits.degreesFahrenheit)) {
            return "°F";
        }
        if (unit.equals(EngineeringUnits.degreesKelvin)) {
            return "K";
        }
        if (unit.equals(EngineeringUnits.pascals)) {
            return "Pa";
        }
        if (unit.equals(EngineeringUnits.kilopascals)) {
            return "kPa";
        }
        if (unit.equals(EngineeringUnits.percent)) {
            return "%";
        }
        if (unit.equals(EngineeringUnits.percentRelativeHumidity)) {
            return "%RH";
        }
        if (unit.equals(EngineeringUnits.watts)) {
            return "W";
        }
        if (unit.equals(EngineeringUnits.kilowatts)) {
            return "kW";
        }
        if (unit.equals(EngineeringUnits.volts)) {
            return "V";
        }
        if (unit.equals(EngineeringUnits.amperes)) {
            return "A";
        }
        if (unit.equals(EngineeringUnits.hertz)) {
            return "Hz";
        }
        if (unit.equals(EngineeringUnits.litersPerSecond)) {
            return "L/s";
        }
        if (unit.equals(EngineeringUnits.metersPerSecond)) {
            return "m/s";
        }
        if (unit.equals(EngineeringUnits.meters)) {
            return "m";
        }
        if (unit.equals(EngineeringUnits.feet)) {
            return "ft";
        }
        if (unit.equals(EngineeringUnits.seconds)) {
            return "s";
        }
        if (unit.equals(EngineeringUnits.minutes)) {
            return "min";
        }
        if (unit.equals(EngineeringUnits.hours)) {
            return "h";
        }
        return unit.toString();
    }
}
