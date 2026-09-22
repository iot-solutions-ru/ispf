package com.ispf.driver.profibus.codec;

/**
 * PROFIBUS FDL constants and clean-room SD2 PDU opcodes used by {@code profibus}.
 */
public final class ProfibusFdlTypes {

    /** Request FDL status with reply (station probe). */
    public static final int FC_REQUEST_FDL_STATUS = 0x49;
    /** Send and request data with reply. */
    public static final int FC_SRD = 0x6C;
    /** Send data with acknowledge. */
    public static final int FC_SDA = 0x4C;

    public static final int PDU_RD_REQ = 0x01;
    public static final int PDU_WR_REQ = 0x02;
    public static final int PDU_RD_RSP = 0x81;
    public static final int PDU_WR_RSP = 0x82;
    public static final int PDU_STATUS = 0x00;

    public static final int DEFAULT_LOCAL_STATION = 2;
    public static final int DEFAULT_REMOTE_STATION = 1;

    private ProfibusFdlTypes() {
    }
}
