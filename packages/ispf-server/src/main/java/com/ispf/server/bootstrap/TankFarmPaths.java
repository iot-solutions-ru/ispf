package com.ispf.server.bootstrap;

/**
 * Object paths for the anonymized tank-farm SCADA mimic demo (scada-mimic widget).
 */
public final class TankFarmPaths {

    public static final String APP_ID = "tank-farm-demo";
    public static final String PLANT_FOLDER_NAME = "tank-farm-demo";
    public static final String DISPLAY_NAME = "Демо — резервуарный парк";
    public static final String FOLDER = "root.platform.devices." + PLANT_FOLDER_NAME;
    public static final String MANIFOLD_HUB = FOLDER + ".manifold-hub";

    public static final String MIMIC = "root.platform.mimics.tank-farm-demo";
    public static final String DASHBOARD = "root.platform.dashboards.tank-farm-hmi";

    public static String tank(int number) {
        return FOLDER + ".tank-" + number;
    }

    private TankFarmPaths() {
    }
}
