package com.ispf.driver.bacnet.codec;

/**
 * Small BACnet engineering-unit subset needed by the driver and tests.
 */
public enum BacnetEngineeringUnit {
    NO_UNITS(95, ""),
    DEGREES_CELSIUS(62, "°C"),
    DEGREES_FAHRENHEIT(64, "°F"),
    DEGREES_KELVIN(63, "K"),
    PASCALS(53, "Pa"),
    KILOPASCALS(54, "kPa"),
    PERCENT(98, "%"),
    PERCENT_RELATIVE_HUMIDITY(29, "%RH"),
    WATTS(47, "W"),
    KILOWATTS(48, "kW"),
    VOLTS(5, "V"),
    AMPERES(3, "A"),
    HERTZ(27, "Hz"),
    LITERS_PER_SECOND(87, "L/s"),
    METERS_PER_SECOND(74, "m/s"),
    METERS(31, "m"),
    FEET(33, "ft"),
    SECONDS(73, "s"),
    MINUTES(72, "min"),
    HOURS(71, "h");

    private final int id;
    private final String haystackUnit;

    BacnetEngineeringUnit(int id, String haystackUnit) {
        this.id = id;
        this.haystackUnit = haystackUnit;
    }

    public int id() {
        return id;
    }

    public String haystackUnit() {
        return haystackUnit;
    }

    public static BacnetEngineeringUnit fromId(int id) {
        for (BacnetEngineeringUnit unit : values()) {
            if (unit.id == id) {
                return unit;
            }
        }
        return null;
    }
}
