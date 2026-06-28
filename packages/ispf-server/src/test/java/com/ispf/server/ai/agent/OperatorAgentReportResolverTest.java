package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorAgentReportResolverTest {

    @Test
    void resolvesShiftReportToDailyEnergy() {
        List<OperatorAgentReportResolver.ReportEntry> catalog = List.of(
                new OperatorAgentReportResolver.ReportEntry(
                        "root.platform.reports.tec-daily-energy",
                        "Суточный журнал энергии",
                        "sql"
                ),
                new OperatorAgentReportResolver.ReportEntry(
                        "root.platform.reports.tec-gpu-run-hours",
                        "Наработка ГПУ",
                        "tree-variables"
                )
        );
        var analysis = OperatorAgentReportResolver.analyze(
                "Запусти сменный отчёт и кратко опиши цифры",
                catalog
        );
        assertEquals("root.platform.reports.tec-daily-energy", analysis.bestPath());
        assertTrue(analysis.terminologyMismatch());
        assertTrue(analysis.needsClarification());
    }

    @Test
    void resolvesGpuReport() {
        List<OperatorAgentReportResolver.ReportEntry> catalog = List.of(
                new OperatorAgentReportResolver.ReportEntry(
                        "root.platform.reports.tec-daily-energy",
                        "Суточный журнал энергии",
                        "sql"
                ),
                new OperatorAgentReportResolver.ReportEntry(
                        "root.platform.reports.tec-gpu-run-hours",
                        "Наработка ГПУ",
                        "tree-variables"
                )
        );
        String path = OperatorAgentReportResolver.resolveBestPath("Покажи наработку ГПУ", catalog);
        assertEquals("root.platform.reports.tec-gpu-run-hours", path);
    }

    @Test
    void detectsUnknownReportIdInUserMessage() {
        List<OperatorAgentReportResolver.ReportEntry> catalog = List.of(
                new OperatorAgentReportResolver.ReportEntry(
                        "root.platform.reports.tec-daily-energy",
                        "Суточный журнал энергии",
                        "sql"
                )
        );
        assertTrue(OperatorAgentReportResolver.mentionsUnknownReportId(
                "mini-tec-shift-report - покажи отчет",
                catalog
        ));
    }
}
