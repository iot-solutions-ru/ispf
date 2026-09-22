package com.ispf.driver.iec101.codec;

/**
 * One information object from an IEC 60870-5-101 ASDU.
 */
public record Iec101Value(int typeId, int ioa, double numeric, boolean bool, String quality) {

    public static Iec101Value measured(int ioa, double value, String quality) {
        return new Iec101Value(Iec101Types.M_ME_NC_1, ioa, value, false, quality);
    }

    public static Iec101Value singlePoint(int ioa, boolean on, String quality) {
        return new Iec101Value(Iec101Types.M_SP_NA_1, ioa, on ? 1.0 : 0.0, on, quality);
    }
}
