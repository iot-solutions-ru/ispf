package com.ispf.driver.dlms.codec;

/**
 * COSEM object classes used by the ISPF DLMS driver.
 */
public enum DlmsObjectType {
    DATA(1),
    REGISTER(3),
    EXTENDED_REGISTER(4),
    DEMAND_REGISTER(5),
    CLOCK(8);

    private final int classId;

    DlmsObjectType(int classId) {
        this.classId = classId;
    }

    public int classId() {
        return classId;
    }
}
