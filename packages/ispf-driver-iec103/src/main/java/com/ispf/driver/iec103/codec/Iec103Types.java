package com.ispf.driver.iec103.codec;

/**
 * ASDU type identifiers and link function codes for IEC 60870-5-103 over FT1.2.
 */
public final class Iec103Types {

    /** Time-tagged message (DPI / protection event). */
    public static final int ASDU_TIME_TAGGED = 1;
    /** Relative time-tagged message. */
    public static final int ASDU_RELATIVE_TIME_TAGGED = 2;
    /** General interrogation. */
    public static final int ASDU_GI = 7;
    /** General interrogation termination. */
    public static final int ASDU_GI_TERMINATION = 8;
    /** Measurands II. */
    public static final int ASDU_MEASURANDS_II = 9;
    /** Generic data / measured float (FUN/INF addressed). */
    public static final int ASDU_GENERIC_DATA = 10;
    /** General command. */
    public static final int ASDU_GENERAL_COMMAND = 20;
    /**
     * Measured float used by existing point mappings that name ASDU 40.
     * Encoded on the wire with the same layout as {@link #ASDU_GENERIC_DATA}.
     */
    public static final int ASDU_MEAS_FLOAT = 40;

    /** IEC 103 cause 1: spontaneous. */
    public static final int COT_SPONTANEOUS = 1;
    /** IEC 103 cause 9: general interrogation. */
    public static final int COT_GENERAL_INTERROGATION = 9;
    /** IEC 103 cause 10: termination of general interrogation. */
    public static final int COT_GI_TERMINATION = 10;
    /** IEC 103 cause 20: positive acknowledgement of command. */
    public static final int COT_COMMAND_ACK = 20;
    /** IEC 103 cause 21: negative acknowledgement of command. */
    public static final int COT_COMMAND_NACK = 21;

    /** Primary function: reset of remote link. */
    public static final int FC_RESET_REMOTE_LINK = 0;
    /** Primary function: user data, confirm expected. */
    public static final int FC_USER_DATA = 3;
    /** Primary function: request class 1 data. */
    public static final int FC_REQUEST_CLASS1 = 10;

    /** Secondary function: positive confirm. */
    public static final int FC_ACK = 0;
    /** Secondary function: user data. */
    public static final int FC_RESPOND_USER_DATA = 8;
    /** Secondary function: requested data not available. */
    public static final int FC_NO_DATA = 9;

    private Iec103Types() {
    }
}
