package com.ispf.driver.iec104.codec;

public record Iec104Value(int ioa, int typeId, Object value, String quality) {

    public static Iec104Value singlePoint(int ioa, boolean value, String quality) {
        return new Iec104Value(ioa, Iec104Type.M_SP_NA_1, value, quality);
    }

    public static Iec104Value doublePoint(int ioa, int value, String quality) {
        return new Iec104Value(ioa, Iec104Type.M_DP_NA_1, value, quality);
    }

    public static Iec104Value normalized(int ioa, double value, String quality) {
        return new Iec104Value(ioa, Iec104Type.M_ME_NA_1, value, quality);
    }

    public static Iec104Value shortFloat(int ioa, double value, String quality) {
        return new Iec104Value(ioa, Iec104Type.M_ME_NC_1, value, quality);
    }

    public boolean booleanValue() {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public double numericValue() {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
