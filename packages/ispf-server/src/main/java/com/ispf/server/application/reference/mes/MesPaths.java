package com.ispf.server.application.reference.mes;

/**
 * Canonical object-tree paths for the platform MES catalog (BL-164).
 */
public final class MesPaths {

    public static final String MES_ROOT = "root.platform.mes";
    public static final String WORK_ORDERS = MES_ROOT + ".work-orders";
    public static final String OPERATIONS = MES_ROOT + ".operations";
    public static final String LOTS = MES_ROOT + ".lots";
    public static final String SHIFTS = MES_ROOT + ".shifts";
    public static final String QUALITY_RECORDS = MES_ROOT + ".quality-records";
    public static final String INSTANCES = MES_ROOT + ".instances";

    public static final String APP_ID = "mes-platform";
    public static final String DISPLAY_NAME = "MES Platform";

    private MesPaths() {
    }
}
