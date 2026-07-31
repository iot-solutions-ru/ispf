package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Report template fill engine selection ({@code poi} | {@code yarg}).
 * LibreOffice settings remain under {@link ReportYargProperties}.
 */
@ConfigurationProperties(prefix = "ispf.reports")
public class ReportEngineProperties {

    /**
     * Spreadsheet Band1 engine: {@code poi} (default, ADR-0053) or {@code yarg} (legacy).
     * DOCX/HTML always use YARG until a dedicated engine exists.
     */
    private String templateEngine = "poi";

    public String getTemplateEngine() {
        return templateEngine;
    }

    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    public boolean usePoiForSpreadsheet() {
        return templateEngine == null || templateEngine.isBlank()
                || "poi".equalsIgnoreCase(templateEngine.trim());
    }
}
