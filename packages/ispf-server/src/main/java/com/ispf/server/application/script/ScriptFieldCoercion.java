package com.ispf.server.application.script;

import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;

final class ScriptFieldCoercion {

    private ScriptFieldCoercion() {
    }

    static Object defaultValue(FieldType type) {
        return switch (type) {
            case BOOLEAN -> false;
            case INTEGER -> 0;
            case LONG -> 0L;
            case DOUBLE -> 0.0;
            case RECORD_LIST -> java.util.List.of();
            default -> "";
        };
    }

    static Object coerce(FieldDefinition field, Object value) {
        if (value == null) {
            return field.nullable() ? null : defaultValue(field.type());
        }
        return switch (field.type()) {
            case BOOLEAN -> coerceBoolean(value);
            case INTEGER -> coerceInteger(value);
            case LONG -> coerceLong(value);
            case DOUBLE -> coerceDouble(value);
            case STRING -> String.valueOf(value);
            default -> value;
        };
    }

    private static Boolean coerceBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        throw new IllegalArgumentException("value must be boolean");
    }

    private static Integer coerceInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            return Integer.parseInt(text.trim());
        }
        throw new IllegalArgumentException("value must be integer");
    }

    private static Long coerceLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text.trim());
        }
        throw new IllegalArgumentException("value must be long");
    }

    private static Double coerceDouble(Object value) {
        if (value instanceof Double doubleValue) {
            return doubleValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            return Double.parseDouble(text.trim());
        }
        throw new IllegalArgumentException("value must be double");
    }
}
