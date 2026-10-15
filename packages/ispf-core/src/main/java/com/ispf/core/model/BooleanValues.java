package com.ispf.core.model;

/**
 * Shared BOOLEAN field coercion: boolean as-is, 0/1 number, explicit string contract.
 */
public final class BooleanValues {

    private BooleanValues() {
    }

    /**
     * Accepts {@link Boolean}, a number exactly 0 or 1, or the strings
     * "true"/"false"/"0"/"1" (trimmed, case-insensitive). Anything else fails.
     */
    public static boolean requireBoolean(String fieldName, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d == 0.0) {
                return false;
            }
            if (d == 1.0) {
                return true;
            }
            throw new IllegalArgumentException(fieldName + " must be boolean");
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
                return true;
            }
            if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
                return false;
            }
            throw new IllegalArgumentException(fieldName + " must be boolean");
        }
        throw new IllegalArgumentException(fieldName + " must be boolean");
    }
}
