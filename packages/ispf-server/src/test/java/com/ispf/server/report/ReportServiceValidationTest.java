package com.ispf.server.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportServiceValidationTest {

    @Test
    void acceptsSelectQuery() {
        ReportService.validateSelectQuery("SELECT 1");
        ReportService.validateSelectQuery("WITH cte AS (SELECT 1) SELECT * FROM cte");
    }

    @Test
    void rejectsForbiddenKeywords() {
        assertThrows(IllegalArgumentException.class, () ->
                ReportService.validateSelectQuery("SELECT 1; DELETE FROM demo_item"));
    }

    @Test
    void reportPathSanitizesReportId() {
        assertEquals("root.platform.reports.ready-items", ReportService.reportPath("ready-items"));
        assertEquals("root.platform.reports.tree-report", ReportService.reportPath("tree-report"));
    }

    @Test
    void bindQueryParametersRepeatsSingleNamedParamForMultiplePlaceholders() {
        String query = "SELECT 1 WHERE (? = '' OR item_code = ?)";
        List<Object> bound = ReportService.bindQueryParameters(
                query,
                List.of("orderNo"),
                Map.of("orderNo", "")
        );
        assertEquals(List.of("", ""), bound);
    }

    @Test
    void countSqlPlaceholdersIgnoresQuestionMarksInStringLiterals() {
        assertEquals(2, ReportService.countSqlPlaceholders("SELECT ? WHERE col = '?' AND id = ?"));
    }
}
