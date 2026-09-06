package com.ispf.driver.secsgem.codec;

/**
 * HSMS / SECS-II constants for the GEM-lab subset.
 */
public final class SecsGemLabTypes {

    public static final int STYPE_DATA = 0;
    public static final int STYPE_SELECT_REQ = 1;
    public static final int STYPE_SELECT_RSP = 2;
    public static final int STYPE_DESELECT_REQ = 3;
    public static final int STYPE_DESELECT_RSP = 4;
    public static final int STYPE_LINKTEST_REQ = 5;
    public static final int STYPE_LINKTEST_RSP = 6;
    public static final int STYPE_REJECT_REQ = 7;
    public static final int STYPE_SEPARATE_REQ = 9;

    public static final int PTYPE_SECS_II = 0;

    public static final int STREAM_1 = 1;
    public static final int STREAM_2 = 2;
    public static final int STREAM_6 = 6;

    public static final int S1F1 = 1;
    public static final int S1F2 = 2;
    public static final int S1F13 = 13;
    public static final int S1F14 = 14;
    public static final int S2F13 = 13;
    public static final int S2F14 = 14;
    public static final int S2F41 = 41;
    public static final int S6F1 = 1;

    private SecsGemLabTypes() {
    }
}
