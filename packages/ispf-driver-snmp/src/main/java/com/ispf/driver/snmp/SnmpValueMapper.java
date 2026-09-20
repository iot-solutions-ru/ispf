package com.ispf.driver.snmp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPermanentException;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;

import java.util.Map;

final class SnmpValueMapper {

    private static final DataSchema NUMERIC_SCHEMA = DataSchema.builder("snmpNumeric")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("snmpString")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("snmpBoolean")
            .field("value", FieldType.BOOLEAN)
            .field("raw", FieldType.STRING)
            .field("type", FieldType.STRING)
            .build();

    private SnmpValueMapper() {
    }

    static DataRecord toRecord(Variable variable, SnmpPoint.ValueKind kind) throws DriverException {
        if (variable == null || variable instanceof Null) {
            throw new DriverPermanentException("SNMP variable is null");
        }
        String typeName = variable.getClass().getSimpleName();
        String raw = variable.toString();

        return switch (kind) {
            case STRING -> DataRecord.single(STRING_SCHEMA, Map.of(
                    "value", raw,
                    "raw", raw,
                    "type", typeName
            ));
            case BOOLEAN -> DataRecord.single(BOOLEAN_SCHEMA, Map.of(
                    "value", parseBoolean(raw),
                    "raw", raw,
                    "type", typeName
            ));
            case INTEGER -> DataRecord.single(NUMERIC_SCHEMA, Map.of(
                    "value", parseNumber(variable),
                    "raw", raw,
                    "type", typeName
            ));
            case AUTO -> autoRecord(variable, raw, typeName);
        };
    }

    private static DataRecord autoRecord(Variable variable, String raw, String typeName) throws DriverException {
        if (variable instanceof OctetString || variable instanceof IpAddress) {
            return DataRecord.single(STRING_SCHEMA, Map.of(
                    "value", raw,
                    "raw", raw,
                    "type", typeName
            ));
        }
        return DataRecord.single(NUMERIC_SCHEMA, Map.of(
                "value", parseNumber(variable),
                "raw", raw,
                "type", typeName
        ));
    }

    static Variable fromRecord(DataRecord value, SnmpPoint.ValueKind kind) throws DriverException {
        return switch (kind) {
            case STRING, AUTO -> new OctetString(extractString(value));
            case BOOLEAN -> new Integer32(Boolean.TRUE.equals(value.firstRow().get("value")) ? 1 : 0);
            case INTEGER -> new Integer32((int) Math.round(extractNumber(value)));
        };
    }

    private static double parseNumber(Variable variable) throws DriverException {
        if (variable instanceof Integer32 v) {
            return v.getValue();
        }
        if (variable instanceof Gauge32 v) {
            return v.getValue();
        }
        if (variable instanceof Counter32 v) {
            return v.getValue();
        }
        if (variable instanceof Counter64 v) {
            return v.getValue();
        }
        if (variable instanceof UnsignedInteger32 v) {
            return v.getValue();
        }
        if (variable instanceof TimeTicks v) {
            return v.getValue();
        }
        try {
            return Double.parseDouble(variable.toString());
        } catch (NumberFormatException e) {
            throw new DriverPermanentException("Cannot convert SNMP value to number: " + variable, e);
        }
    }

    private static boolean parseBoolean(String raw) {
        return switch (raw.trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static double extractNumber(DataRecord value) {
        Object numeric = value.firstRow().get("value");
        if (numeric instanceof Number number) {
            return number.doubleValue();
        }
        Object raw = value.firstRow().get("raw");
        if (raw != null) {
            return Double.parseDouble(raw.toString());
        }
        throw new IllegalArgumentException("SNMP write requires numeric value/raw field");
    }

    private static String extractString(DataRecord value) {
        Object text = value.firstRow().get("value");
        if (text != null) {
            return text.toString();
        }
        Object raw = value.firstRow().get("raw");
        if (raw != null) {
            return raw.toString();
        }
        throw new IllegalArgumentException("SNMP write requires value/raw field");
    }
}
