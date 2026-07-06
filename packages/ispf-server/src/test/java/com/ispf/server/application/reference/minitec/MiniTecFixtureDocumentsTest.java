package com.ispf.server.application.reference.minitec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniTecFixtureDocumentsTest {

    @Test
    void kpiDashboardUsesTreeFirstReportPaths() {
        String layout = MiniTecFixtureDocuments.dashboardLayout("mini-tec-kpi");
        assertTrue(layout.contains(MiniTecPaths.REPORT_DAILY_ENERGY));
        assertTrue(layout.contains(MiniTecPaths.REPORT_GPU_RUN_HOURS));
        assertTrue(!layout.contains("root.platform.applications.mini-tec.reports."));
    }
}
