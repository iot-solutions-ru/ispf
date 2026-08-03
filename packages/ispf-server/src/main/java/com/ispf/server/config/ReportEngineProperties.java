package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Report template fill engine selection.
 */
@ConfigurationProperties(prefix = "ispf.reports")
public class ReportEngineProperties {

    /**
     * Spreadsheet Band1 engine: {@code poi} (default, ADR-0053).
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
