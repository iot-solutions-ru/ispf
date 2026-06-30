package com.ispf.server.bootstrap;

/**
 * Object paths for pipeline SCADA demo (РД-029 типовые экранные формы).
 */
public final class PipelineScadaPaths {

    public static final String APP_ID = "pipeline-scada";
    public static final String PLANT_FOLDER_NAME = "pipeline-scada";
    public static final String DISPLAY_NAME = "СДКУ — магистральный нефтепровод";
    public static final String FOLDER = "root.platform.devices." + PLANT_FOLDER_NAME;
    public static final String MANIFOLD_HUB = FOLDER + ".manifold-hub";

    /** Main HMI entry — экранная форма РП (§6.4). */
    public static final String DASHBOARD = "root.platform.dashboards.pipeline-scada-hmi";

    /** @deprecated alias for {@link #MIMIC_RP} */
    @Deprecated
    public static final String MIMIC_TANK_FARM_DEMO = "root.platform.mimics.tank-farm-demo";

    public static final String MIMIC_MT_TERRITORIAL = "root.platform.mimics.pipeline-mt-territorial";
    public static final String MIMIC_MT_SCHEME = "root.platform.mimics.pipeline-mt-scheme";
    public static final String MIMIC_RP_OIL_PLACEMENT = "root.platform.mimics.pipeline-rp-oil-placement";
    public static final String MIMIC_RP = "root.platform.mimics.pipeline-rp";
    public static final String MIMIC_RP_URDO = "root.platform.mimics.pipeline-rp-urdo";
    public static final String MIMIC_SIKN = "root.platform.mimics.pipeline-sikn";
    public static final String MIMIC_PSP = "root.platform.mimics.pipeline-psp";
    public static final String MIMIC_NPS = "root.platform.mimics.pipeline-nps";
    public static final String MIMIC_LU_MT = "root.platform.mimics.pipeline-lu-mt";
    public static final String MIMIC_LU_NAV = "root.platform.mimics.pipeline-lu-nav";
    public static final String MIMIC_SEA_TERMINAL = "root.platform.mimics.pipeline-sea-terminal";
    public static final String MIMIC_PIER = "root.platform.mimics.pipeline-pier";
    public static final String MIMIC_MT_STOP_PANEL = "root.platform.mimics.pipeline-mt-stop-panel";
    public static final String MIMIC_MT_SECTION_PANEL = "root.platform.mimics.pipeline-mt-section-panel";
    public static final String MIMIC_NPS_PANEL = "root.platform.mimics.pipeline-nps-panel";

    public static String tank(int number) {
        return FOLDER + ".tank-" + number;
    }

    private PipelineScadaPaths() {
    }
}
