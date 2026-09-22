package com.ispf.server.report;

import com.ispf.server.config.ReportLibreOfficeProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformReportHealthServiceTest {

    @Test
    void healthJsonMatchesTheConsoleCard() throws Exception {
        ReportLibreOfficeProperties properties = new ReportLibreOfficeProperties();
        properties.setPath("C:/missing/libreoffice/program");
        properties.setTimeoutSeconds(60);

        PlatformReportHealthService.ReportHealth health = new PlatformReportHealthService(properties).health();
        JsonNode json = JsonMapper.builder().build().valueToTree(health);

        assertFalse(json.get("libreOfficeAvailable").asBoolean());
        assertEquals("C:/missing/libreoffice/program", json.get("configuredPath").asText());
        assertTrue(json.get("resolvedPath").isNull());
        assertEquals(60, json.get("timeoutSeconds").asInt());
        assertTrue(json.get("pdfHint").asText().contains("LibreOffice"));
        assertFalse(json.has("ports"));
    }
}
