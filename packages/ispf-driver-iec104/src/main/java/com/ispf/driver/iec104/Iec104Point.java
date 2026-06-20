package com.ispf.driver.iec104;

import com.ispf.driver.DriverException;

/**
 * Parsed IEC 60870-5-104 point reference from mapping string {@code ioa:dataType}.
 * Example: {@code 2001:FLOAT}.
 */
record Iec104Point(int ioa, Iec104DataType dataType) {

    enum Iec104DataType {
        BOOL,
        INT,
        FLOAT,
        M_SP_NA_1,
        M_ME_NA_1,
        M_ME_NC_1,
        M_ME_TF_1
    }

    static Iec104Point parse(String mapping) throws DriverException {
        String[] parts = mapping.split(":");
        if (parts.length < 2) {
            throw new DriverException("Invalid IEC104 mapping (expected ioa:dataType): " + mapping);
        }
        try {
            int ioa = Integer.parseInt(parts[0].trim());
            Iec104DataType dataType = Iec104DataType.valueOf(parts[1].trim().toUpperCase());
            return new Iec104Point(ioa, dataType);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Invalid IEC104 mapping: " + mapping, e);
        }
    }
}
