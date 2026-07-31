package com.ispf.server.report;

import java.util.List;
import java.util.Map;

/**
 * Fills Band1 Office templates with report rows (ADR-0053).
 */
public interface ReportTemplateEngine {

    /** Whether this engine handles the given stored template format ({@code xlsx}, {@code docx}, …). */
    boolean supportsTemplateFormat(String templateFormat);

    void validate(byte[] content, String format);

    TemplateExportResult fill(
            ReportService.ReportView report,
            ReportTemplateStore.StoredTemplate template,
            List<Map<String, Object>> rows,
            ReportExportFormat outputFormat
    );
}
