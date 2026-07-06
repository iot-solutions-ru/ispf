package com.ispf.server.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServicePathTest {

    @Test
    void resolvesLegacyApplicationReportPath() {
        assertThat(ReportService.resolveReportPath(
                "root.platform.applications.mini-tec.reports.tec-daily-energy"
        )).isEqualTo("root.platform.reports.tec-daily-energy");

        assertThat(ReportService.resolveReportPath(
                "root.platform.applications.mini-tec.reports.tec-gpu-run-hours"
        )).isEqualTo("root.platform.reports.tec-gpu-run-hours");
    }

    @Test
    void leavesCanonicalPathUnchanged() {
        assertThat(ReportService.resolveReportPath("root.platform.reports.tec-daily-energy"))
                .isEqualTo("root.platform.reports.tec-daily-energy");
    }
}
