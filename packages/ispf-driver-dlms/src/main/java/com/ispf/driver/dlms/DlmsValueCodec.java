package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPermanentException;

import java.nio.charset.StandardCharsets;

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
            throw new DriverPermanentException("DLMS write requires value or raw field");
        }
        return switch (point.objectType()) {
            case REGISTER, EXTENDED_REGISTER, DEMAND_REGISTER -> toDouble(raw);
            case DATA -> raw.toString();
            case CLOCK -> raw;
            default -> raw;
        };
    }

    static double toDouble(Object raw) throws DriverException {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new DriverPermanentException("DLMS write requires numeric value: " + raw, ex);
        }
    }

    static Object formatReadValue(Object raw) {
        if (raw instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return raw;
    }
}
