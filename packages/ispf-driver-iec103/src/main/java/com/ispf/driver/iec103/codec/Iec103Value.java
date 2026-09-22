package com.ispf.driver.iec103.codec;

/**
 * One FUN/INF information object decoded from an IEC 60870-5-103 ASDU.
 */
public record Iec103Value(int typeId, int fun, int inf, double numeric, boolean bool, String quality) {

    public int packedIoa() {
        return ((fun & 0xFF) << 8) | (inf & 0xFF);
    }

    public static Iec103Value measured(int fun, int inf, double value, String quality) {
        return new Iec103Value(Iec103Types.ASDU_MEAS_FLOAT, fun, inf, value, false, quality);
    }

    public static Iec103Value genericData(int fun, int inf, double value, String quality) {
        return new Iec103Value(Iec103Types.ASDU_GENERIC_DATA, fun, inf, value, false, quality);
    }

    public static Iec103Value measurandsIi(int fun, int inf, double value, String quality) {
        return new Iec103Value(Iec103Types.ASDU_MEASURANDS_II, fun, inf, value, false, quality);
    }

    public static Iec103Value status(int fun, int inf, boolean on, String quality) {
        return new Iec103Value(Iec103Types.ASDU_TIME_TAGGED, fun, inf, on ? 1.0 : 0.0, on, quality);
    }
}
