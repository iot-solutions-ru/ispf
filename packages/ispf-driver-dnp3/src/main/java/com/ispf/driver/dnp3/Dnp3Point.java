package com.ispf.driver.dnp3;

import com.ispf.driver.DriverException;

/**
 * Parsed DNP3 point reference from mapping string {@code index:dataType}.
 * Example: {@code 0:ANALOG_INPUT}.
 */
record Dnp3Point(int index, Dnp3DataType dataType) {

    enum Dnp3DataType {
        BINARY_INPUT,
        BINARY_OUTPUT,
        ANALOG_INPUT,
        ANALOG_OUTPUT,
        COUNTER
    }

    static Dnp3Point parse(String mapping) throws DriverException {
        String[] parts = mapping.split(":");
        if (parts.length < 2) {
            throw new DriverException("Invalid DNP3 mapping (expected index:dataType): " + mapping);
        }
        try {
            int index = Integer.parseInt(parts[0].trim());
            Dnp3DataType dataType = Dnp3DataType.valueOf(parts[1].trim().toUpperCase());
            return new Dnp3Point(index, dataType);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Invalid DNP3 mapping: " + mapping, e);
        }
    }
}
