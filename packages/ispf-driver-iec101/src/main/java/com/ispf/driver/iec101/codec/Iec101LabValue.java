package com.ispf.driver.iec101.codec;

/**
 * One information object decoded by the IEC101-lab codec.
 */
public record Iec101LabValue(int typeId, int ioa, double numeric, boolean bool, String quality) {

    public static Iec101LabValue measured(int ioa, double value, String quality) {
        return new Iec101LabValue(Iec101LabTypes.M_ME_NC_1, ioa, value, false, quality);
    }

    public static Iec101LabValue singlePoint(int ioa, boolean on, String quality) {
        return new Iec101LabValue(Iec101LabTypes.M_SP_NA_1, ioa, on ? 1.0 : 0.0, on, quality);
    }
}
