package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.driver.DriverException;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.ObjectType;

/**
 * Value coercion for DLMS read/write.
 */
final class DlmsValueCodec {

    private DlmsValueCodec() {
    }

    static Object extractWriteValue(DataRecord value, DlmsPoint point) throws DriverException {
        Object raw = value.firstRow().get("raw");
        if (raw == null) {
            raw = value.firstRow().get("value");
        }
        if (raw == null) {
            throw new DriverException("DLMS write requires value or raw field");
        }
        return switch (point.objectType()) {
            case REGISTER, EXTENDED_REGISTER, DEMAND_REGISTER -> toDouble(raw);
            case DATA -> raw.toString();
            case CLOCK -> raw;
            default -> raw;
        };
    }

    static DataType writeDataType(DlmsPoint point, Object raw) {
        return switch (point.objectType()) {
            case REGISTER, EXTENDED_REGISTER, DEMAND_REGISTER -> DataType.FLOAT64;
            case DATA -> DataType.OCTET_STRING;
            default -> DataType.NONE;
        };
    }

    static double toDouble(Object raw) throws DriverException {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new DriverException("DLMS write requires numeric value: " + raw, ex);
        }
    }

    static Object formatReadValue(Object raw) {
        if (raw instanceof byte[] bytes) {
            return new String(bytes);
        }
        return raw;
    }
}
