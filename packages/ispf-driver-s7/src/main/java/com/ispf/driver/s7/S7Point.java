package com.ispf.driver.s7;

import com.github.s7connector.api.DaveArea;
import com.ispf.driver.DriverException;

/**
 * Parsed S7 point reference from mapping string {@code area:dbNumber:offset:type}.
 * Example: {@code DB:1:0:REAL}.
 */
record S7Point(DaveArea area, int dbNumber, int offset, S7DataType dataType) {

    enum S7DataType {
        BOOL(1),
        BYTE(1),
        SINT(1),
        USINT(1),
        INT(2),
        UINT(2),
        WORD(2),
        DINT(4),
        UDINT(4),
        DWORD(4),
        REAL(4),
        LREAL(8);

        private final int byteLength;

        S7DataType(int byteLength) {
            this.byteLength = byteLength;
        }

        int byteLength() {
            return byteLength;
        }
    }

    static S7Point parse(String mapping) throws DriverException {
        String[] parts = mapping.split(":");
        if (parts.length < 4) {
            throw new DriverException("Invalid S7 mapping (expected area:dbNumber:offset:type): " + mapping);
        }
        try {
            DaveArea area = parseArea(parts[0].trim());
            int dbNumber = Integer.parseInt(parts[1].trim());
            int offset = Integer.parseInt(parts[2].trim());
            S7DataType dataType = S7DataType.valueOf(parts[3].trim().toUpperCase());
            return new S7Point(area, dbNumber, offset, dataType);
        } catch (IllegalArgumentException e) {
            throw new DriverException("Invalid S7 mapping: " + mapping, e);
        }
    }

    private static DaveArea parseArea(String areaName) throws DriverException {
        return switch (areaName.toUpperCase()) {
            case "DB" -> DaveArea.DB;
            case "INPUT", "I", "E" -> DaveArea.INPUTS;
            case "OUTPUT", "Q", "A" -> DaveArea.OUTPUTS;
            case "FLAGS", "M" -> DaveArea.FLAGS;
            case "TIMER", "T" -> DaveArea.TIMER;
            case "COUNTER", "C" -> DaveArea.COUNTER;
            default -> throw new DriverException("Unknown S7 area: " + areaName);
        };
    }
}
