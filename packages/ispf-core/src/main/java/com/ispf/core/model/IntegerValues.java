package com.ispf.core.model;

/**
 * Shared INTEGER field checks: whole number in {@code int} range, no silent truncation.
 */
public final class IntegerValues {

    private IntegerValues() {
    }

    /**
     * Accepts a whole number in {@link Integer#MIN_VALUE}…{@link Integer#MAX_VALUE}.
     * Fractional numbers and values outside that range fail; {@link Long} in range is accepted.
     */
    public static int requireInt(String fieldName, Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Long longValue) {
            if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(fieldName + " is out of integer range");
            }
            return longValue.intValue();
        }
        if (value instanceof Short || value instanceof Byte) {
            return ((Number) value).intValue();
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (!Double.isFinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(fieldName + " must be integer");
            }
            if (d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(fieldName + " is out of integer range");
            }
            return (int) d;
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(fieldName + " must be integer", ex);
            }
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (!Double.isFinite(d) || d != Math.rint(d)) {
                throw new IllegalArgumentException(fieldName + " must be integer");
            }
            long asLong = number.longValue();
            if (asLong < Integer.MIN_VALUE || asLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(fieldName + " is out of integer range");
            }
            return (int) asLong;
        }
        throw new IllegalArgumentException(fieldName + " must be integer");
    }
}
