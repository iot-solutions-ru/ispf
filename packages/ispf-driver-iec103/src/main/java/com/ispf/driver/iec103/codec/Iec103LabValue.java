package com.ispf.driver.iec103.codec;

/**
 * One FUN/INF information object decoded by the IEC103-lab codec.
 */
public record Iec103LabValue(int typeId, int fun, int inf, double numeric, boolean bool, String quality) {

    public int packedIoa() {
        return ((fun & 0xFF) << 8) | (inf & 0xFF);
    }

    public static Iec103LabValue measured(int fun, int inf, double value, String quality) {
        return new Iec103LabValue(Iec103LabTypes.ASDU_LAB_MEAS_FLOAT, fun, inf, value, false, quality);
    }

    public static Iec103LabValue measurandsIi(int fun, int inf, double value, String quality) {
        return new Iec103LabValue(Iec103LabTypes.ASDU_MEASURANDS_II, fun, inf, value, false, quality);
    }

    public static Iec103LabValue status(int fun, int inf, boolean on, String quality) {
        return new Iec103LabValue(Iec103LabTypes.ASDU_TIME_TAGGED, fun, inf, on ? 1.0 : 0.0, on, quality);
    }
}
