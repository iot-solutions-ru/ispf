package com.ispf.driver.iec101.codec;

/**
 * Type ids and link function codes for the unbalanced IEC 60870-5-101 profile
 * implemented by this driver (COT 1 byte, common address 2 bytes, IOA 2 bytes).
 */
public final class Iec101Types {

    public static final int M_SP_NA_1 = 1;
    public static final int M_ME_NC_1 = 13;
    public static final int C_SC_NA_1 = 45;
    public static final int C_SE_NC_1 = 50;
    public static final int C_IC_NA_1 = 100;

    public static final int COT_ACTIVATION = 6;
    public static final int COT_ACTIVATION_CON = 7;
    public static final int COT_ACTIVATION_TERMINATION = 10;
    public static final int COT_INTERROGATED = 20;

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

    public static final int QOI_STATION = 20;

    private Iec101Types() {
    }
}
