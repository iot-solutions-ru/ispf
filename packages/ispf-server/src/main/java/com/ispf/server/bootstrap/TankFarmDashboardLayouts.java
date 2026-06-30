package com.ispf.server.bootstrap;

/**
 * Dashboard layout for the anonymized tank-farm SCADA mimic demo.
 */
public final class TankFarmDashboardLayouts {

    private static final String STYLES =
            "\"stylesJson\":\"{\\\"background\\\":\\\"#c0c0c0\\\",\\\"color\\\":\\\"#000000\\\"}\"";

    public static final String HMI_LAYOUT = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"mimic","type":"scada-mimic","title":"Мнемосхема резервуарного парка","x": 0,"y": 0,"w": 84,"h": 63,
            "mimicPath":"%s","panEnabled":true,"defaultZoom":1}
            ]}
            """.formatted(STYLES, TankFarmPaths.MIMIC).replace("\n", "").replace("  ", "");

    private TankFarmDashboardLayouts() {
    }
}
