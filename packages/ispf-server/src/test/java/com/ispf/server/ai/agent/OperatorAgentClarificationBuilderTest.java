package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorAgentClarificationBuilderTest {

    @Test
    void clarifiesShiftReportTerminology() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "tool", "tool", "list_reports", "result", Map.of(
                        "status", "OK",
                        "reports", List.of(
                                Map.of(
                                        "path", "root.platform.reports.tec-daily-energy",
                                        "title", "Суточный журнал энергии",
                                        "reportType", "sql"
                                ),
                                Map.of(
                                        "path", "root.platform.reports.tec-gpu-run-hours",
                                        "title", "Наработка ГПУ",
                                        "reportType", "tree-variables"
                                )
                        )
                ))
        );
        var clarification = OperatorAgentClarificationBuilder.maybeAfterListReports(
                steps,
                "Запусти сменный отчёт и кратко опиши цифры"
        );
        assertTrue(clarification.isPresent());
        assertTrue(clarification.get().summary().contains("смен"));
        assertTrue(clarification.get().summary().contains("Суточный журнал энергии"));
        assertTrue(Boolean.TRUE.equals(clarification.get().result().get("interactive")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions =
                (List<Map<String, Object>>) clarification.get().result().get("suggestions");
        assertFalse(suggestions.isEmpty());
        assertEquals("root.platform.reports.tec-daily-energy", suggestions.getFirst().get("path"));
    }

    @Test
    void skipsClarificationForExactGpuReportName() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "tool", "tool", "list_reports", "result", Map.of(
                        "status", "OK",
                        "reports", List.of(
                                Map.of(
                                        "path", "root.platform.reports.tec-daily-energy",
                                        "title", "Суточный журнал энергии",
                                        "reportType", "sql"
                                ),
                                Map.of(
                                        "path", "root.platform.reports.tec-gpu-run-hours",
                                        "title", "Наработка ГПУ",
                                        "reportType", "tree-variables"
                                )
                        )
                ))
        );
        var clarification = OperatorAgentClarificationBuilder.maybeAfterListReports(
                steps,
                "Запусти отчёт Наработка ГПУ"
        );
        assertTrue(clarification.isEmpty());
    }
}
