package com.ispf.driver.iec103.codec;

/**
 * ASDU type identifiers used by the IEC103-lab subset.
 * <p>
 * Numeric IDs match IEC 60870-5-103 where applicable. Type {@code 40} is a
 * <strong>lab-only</strong> measured-float ASDU (not a full 103 private range claim).
 */
public final class Iec103LabTypes {

    /** Time-tagged message (DPI / protection event style). */
    public static final int ASDU_TIME_TAGGED = 1;
    /** Measurands II (lab: one short float + quality). */
    public static final int ASDU_MEASURANDS_II = 9;
    /** General interrogation. */
    public static final int ASDU_GI = 7;
    /** General interrogation termination. */
    public static final int ASDU_GI_TERMINATION = 8;
    /** General command. */
    public static final int ASDU_GENERAL_COMMAND = 20;
    /** Lab measured float (FUN/INF addressed), not full serial 103. */
    public static final int ASDU_LAB_MEAS_FLOAT = 40;

    public static final int COT_ACTIVATION = 6;
    public static final int COT_ACTIVATION_CON = 7;
    public static final int COT_INTERROGATED = 9;
    public static final int COT_SPONTANEOUS = 1;

    private Iec103LabTypes() {
    }
}
