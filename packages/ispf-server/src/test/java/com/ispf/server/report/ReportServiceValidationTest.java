package com.ispf.server.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportServiceValidationTest {

    @Test
    void acceptsSelectQuery() {
        ReportSqlQuery.validateSelectQuery("SELECT 1");
        ReportSqlQuery.validateSelectQuery("WITH cte AS (SELECT 1) SELECT * FROM cte");
    }

    @Test
    void rejectsForbiddenKeywords() {
        assertThrows(IllegalArgumentException.class, () ->
                ReportSqlQuery.validateSelectQuery("SELECT 1; DELETE FROM demo_item"));
    }

    @Test
    void reportPathSanitizesReportId() {
        assertEquals("root.platform.reports.ready-items", ReportService.reportPath("ready-items"));
        assertEquals("root.platform.reports.tree-report", ReportService.reportPath("tree-report"));
    }

    @Test
    void missingDeclaredParameterIsNotFilledWithEmptyString() {
        Map<String, Object> effective = ReportSqlQuery.effectiveParameters(
                Map.of(),
                Map.of("to", "2027-12-31T23:59:59Z")
        );
        assertEquals("2027-12-31T23:59:59Z", effective.get("to"));
        assertFalse(effective.containsKey("from"));

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () ->
                ReportSqlQuery.bindQueryParameters(
                        "SELECT CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP)",
                        List.of("from", "to"),
                        effective
                ));
        assertEquals("Missing report parameter: from", missing.getMessage());
    }

    @Test
    void defaultParameterSuppliesOmittedInput() {
        Map<String, Object> effective = ReportSqlQuery.effectiveParameters(
                Map.of("from", "2027-01-01T00:00:00Z"),
                Map.of("to", "2027-12-31T23:59:59Z")
        );
        List<Object> bound = ReportSqlQuery.bindQueryParameters(
                "SELECT CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP)",
                List.of("from", "to"),
                effective
        );
        assertEquals(List.of("2027-01-01T00:00:00Z", "2027-12-31T23:59:59Z"), bound);
    }

    @Test
    void rejectsStructuredReportParameter() {
        String query = "SELECT CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP)";
        IllegalArgumentException array = assertThrows(IllegalArgumentException.class, () ->
                ReportSqlQuery.bindQueryParameters(
                        query,
                        List.of("from", "to"),
                        Map.of("from", List.of("2020"), "to", "2027-12-31T23:59:59Z")
                ));
        assertEquals("Report parameter from must be a scalar value", array.getMessage());

        IllegalArgumentException object = assertThrows(IllegalArgumentException.class, () ->
                ReportSqlQuery.bindQueryParameters(
                        query,
                        List.of("from", "to"),
                        Map.of("from", Map.of("x", "2020"), "to", "2027-12-31T23:59:59Z")
                ));
        assertEquals("Report parameter from must be a scalar value", object.getMessage());
    }

    @Test
    void rejectsNonTemporalValueForTimestampCast() {
        String query = "SELECT CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP)";
        assertTemporalRejected(query, true);
        assertTemporalRejected(query, 123);
        assertTemporalRejected(query, "[\"2020\"]");
        List<Object> bound = ReportSqlQuery.bindQueryParameters(
                query,
                List.of("from", "to"),
                Map.of("from", "2027-01-01 00:00:00", "to", "2027-12-31T23:59:59Z")
        );
        assertEquals(List.of("2027-01-01 00:00:00", "2027-12-31T23:59:59Z"), bound);
    }

    private static void assertTemporalRejected(String query, Object from) {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () ->
                ReportSqlQuery.bindQueryParameters(
                        query,
                        List.of("from", "to"),
                        Map.of("from", from, "to", "2027-01-01 00:00:00")
                ));
        assertEquals("Report parameter from must be a timestamp", rejected.getMessage());
    }

    @Test
    void reportBelongsOnlyToItsApplicationDataSource() {
        assertEquals(true, ReportService.reportBelongsToApplication(
                "coldchain-control",
                "root.platform.data-sources.coldchain-control",
                "",
                "root.platform.data-sources.coldchain-control"
        ));
        assertEquals(false, ReportService.reportBelongsToApplication(
                "no-such-app",
                "root.platform.data-sources.coldchain-control",
                "",
                "root.platform.data-sources.no-such-app"
        ));
        assertEquals(true, ReportService.reportBelongsToApplication(
                "coldchain-control",
                "root.platform.data-sources.other",
                "coldchain-control",
                "root.platform.data-sources.coldchain-control"
        ));
    }

    @Test
    void rejectsNonPositiveMaxRows() {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () ->
                ReportService.requirePositiveMaxRows(-1));
        assertEquals("Report maxRows must be at least 1", rejected.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ReportService.requirePositiveMaxRows(0));
        ReportService.requirePositiveMaxRows(1);
    }

    @Test
    void bindQueryParametersRepeatsSingleNamedParamForMultiplePlaceholders() {
        String query = "SELECT 1 WHERE (? = '' OR item_code = ?)";
        List<Object> bound = ReportSqlQuery.bindQueryParameters(
                query,
                List.of("orderNo"),
                Map.of("orderNo", "")
        );
        assertEquals(List.of("", ""), bound);
    }

    @Test
    void countSqlPlaceholdersIgnoresQuestionMarksInStringLiterals() {
        assertEquals(2, ReportSqlQuery.countSqlPlaceholders("SELECT ? WHERE col = '?' AND id = ?"));
    }
}
