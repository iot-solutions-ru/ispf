package com.ispf.driver.iec101.codec;

/**
 * ASDU type identifiers used by the IEC101-lab subset (same numeric IDs as IEC 60870-5-101/104).
 */
public final class Iec101LabTypes {

    public static final int M_SP_NA_1 = 1;
    public static final int M_ME_NC_1 = 13;
    public static final int C_SC_NA_1 = 45;
    public static final int C_SE_NC_1 = 50;
    public static final int C_IC_NA_1 = 100;

    public static final int COT_ACTIVATION = 6;
    public static final int COT_ACTIVATION_CON = 7;
    public static final int COT_INTERROGATED = 20;
    public static final int COT_SPONTANEOUS = 3;

    private Iec101LabTypes() {
    }
}
