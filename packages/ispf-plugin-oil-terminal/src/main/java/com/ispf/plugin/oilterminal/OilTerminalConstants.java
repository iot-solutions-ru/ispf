package com.ispf.plugin.oilterminal;

/**
 * Oil terminal reference stand — model names and object tree paths.
 */
public final class OilTerminalConstants {

    public static final String ROOT = "root.platform.oil-terminal";
    public static final String TANKS = ROOT + ".tanks";
    public static final String RACKS = ROOT + ".racks";
    public static final String ORDERS = ROOT + ".orders";
    public static final String SAMPLES = ROOT + ".samples";
    public static final String WORKFLOWS = ROOT + ".workflows";
    public static final String DASHBOARDS = ROOT + ".dashboards";

    public static final String MODEL_TANK = "oil-tank-v1";
    public static final String MODEL_RACK = "oil-rack-v1";
    public static final String MODEL_DISPATCH = "oil-dispatch-order-v1";
    public static final String MODEL_SAMPLE = "oil-sample-v1";

    public static final String DEMO_TANK = "rvs3";
    public static final String DEMO_RACK = "rack2";
    public static final String DEMO_ORDER = "dispatch4521";

    public static final String EVENT_DISPATCH_STARTED = "dispatchStarted";
    public static final String EVENT_DISPATCH_COMPLETED = "dispatchCompleted";
    public static final String EVENT_DISPATCH_CANCELLED = "dispatchCancelled";
    public static final String EVENT_TANK_LEVEL_LOW = "tankLevelLow";
    public static final String EVENT_LAB_APPROVED = "labApproved";

    private OilTerminalConstants() {
    }

    public static String tankPath(String name) {
        return TANKS + "." + name;
    }

    public static String rackPath(String name) {
        return RACKS + "." + name;
    }

    public static String orderPath(String name) {
        return ORDERS + "." + name;
    }

    public static String samplePath(String name) {
        return SAMPLES + "." + name;
    }
}
