package com.ispf.driver.s7;

import com.github.s7connector.api.DaveArea;
import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverException;

import java.util.Locale;
/**
 * Parsed S7 point reference from mapping string {@code area:dbNumber:offset:type}.
 * BOOL may address one bit inside the byte: {@code DB:1:0.3:BOOL} is byte 0, bit 3.
 * A bare offset ({@code DB:1:0:BOOL}) is bit 0.
 */
record S7Point(DaveArea area, int dbNumber, int offset, int bitOffset, S7DataType dataType) {

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
            throw new DriverConfigurationException("Invalid S7 mapping (expected area:dbNumber:offset:type): " + mapping);
        }
        try {
            DaveArea area = parseArea(parts[0].trim());
            int dbNumber = Integer.parseInt(parts[1].trim());
            OffsetAndBit address = parseOffset(parts[2].trim());
            S7DataType dataType = S7DataType.valueOf(parts[3].trim().toUpperCase(Locale.ROOT));
            if (dataType == S7DataType.BOOL) {
                if (address.bitOffset() < 0 || address.bitOffset() > 7) {
                    throw new DriverConfigurationException("S7 BOOL bit offset must be 0..7: " + mapping);
                }
            } else if (address.explicitBit()) {
                throw new DriverConfigurationException("S7 bit offset is only valid for BOOL: " + mapping);
            }
            return new S7Point(area, dbNumber, address.offset(), address.bitOffset(), dataType);
        } catch (IllegalArgumentException e) {
            throw new DriverConfigurationException("Invalid S7 mapping: " + mapping, e);
        }
    }

    private record OffsetAndBit(int offset, int bitOffset, boolean explicitBit) {}

    private static OffsetAndBit parseOffset(String raw) {
        int dot = raw.indexOf('.');
        if (dot < 0) {
            return new OffsetAndBit(Integer.parseInt(raw), 0, false);
        }
        if (dot == 0 || dot == raw.length() - 1) {
            throw new IllegalArgumentException("S7 offset must be byte or byte.bit: " + raw);
        }
        return new OffsetAndBit(
                Integer.parseInt(raw.substring(0, dot)),
                Integer.parseInt(raw.substring(dot + 1)),
                true);
    }

    private static DaveArea parseArea(String areaName) throws DriverException {
        return switch (areaName.toUpperCase(Locale.ROOT)) {
            case "DB" -> DaveArea.DB;
            case "INPUT", "I", "E" -> DaveArea.INPUTS;
            case "OUTPUT", "Q", "A" -> DaveArea.OUTPUTS;
            case "FLAGS", "M" -> DaveArea.FLAGS;
            case "TIMER", "T" -> DaveArea.TIMER;
            case "COUNTER", "C" -> DaveArea.COUNTER;
            default -> throw new DriverConfigurationException("Unknown S7 area: " + areaName);
        };
    }
}
