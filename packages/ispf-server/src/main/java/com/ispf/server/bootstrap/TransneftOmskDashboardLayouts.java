package com.ispf.server.bootstrap;

/**
 * Dashboard layout for the Transneft Omsk RDP SCADA mimic demo.
 */
public final class TransneftOmskDashboardLayouts {

    private static final String STYLES =
            "\"stylesJson\":\"{\\\"background\\\":\\\"#0d1117\\\",\\\"color\\\":\\\"#e6edf3\\\"}\"";

    public static final String RDP_MIMIC = """
            {"columns": 84,"rowHeight": 8,%s,"widgets":[
            {"id":"title","type":"html-snippet","title":"Заголовок","x": 0,"y": 0,"w": 84,"h": 7,
            "htmlJson":"<h2 style=\\"margin:0;color:#e6edf3\\">РДП Омск — мнемосхема резервуарного парка (демо)</h2>"},
            {"id":"mimic","type":"scada-mimic","title":"Мнемосхема РДП Омск","x": 0,"y": 7,"w": 84,"h": 56,
            "mimicPath":"%s","panEnabled":true,"defaultZoom":0.95}
            ]}
            """.formatted(STYLES, TransneftOmskPaths.MIMIC_RDP).replace("\n", "").replace("  ", "");

    private TransneftOmskDashboardLayouts() {
    }
}
