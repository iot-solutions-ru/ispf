package com.ispf.server.bootstrap;

/**
 * Dashboard layouts for pipeline SCADA HMI forms (РД-029).
 */
public final class PipelineScadaDashboardLayouts {

    private static final String STYLES =
            "\"stylesJson\":\"{\\\"background\\\":\\\"#c0c0c0\\\",\\\"color\\\":\\\"#000000\\\"}\"";

    public static String hmiLayout(String mimicPath, String widgetTitle) {
        String safeTitle = widgetTitle.replace("\"", "\\\"");
        return """
                {"columns": 84,"rowHeight": 8,%s,"widgets":[
                {"id":"mimic","type":"scada-mimic","title":"%s","x": 0,"y": 0,"w": 84,"h": 63,
                "mimicPath":"%s","panEnabled":true,"defaultZoom":1}
                ]}
                """.formatted(STYLES, safeTitle, mimicPath).replace("\n", "").replace("  ", "");
    }

    private PipelineScadaDashboardLayouts() {
    }
}
