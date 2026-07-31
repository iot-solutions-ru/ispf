package com.ispf.server.report;

import com.ispf.server.config.ReportEngineProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Selects POI vs YARG for Band1 templates (ADR-0053).
 */
@Service
public class ReportTemplateRouter {

    private final ReportEngineProperties engineProperties;
    private final PoiSpreadsheetTemplateEngine poiEngine;
    private final YargReportService yargReportService;

    public ReportTemplateRouter(
            ReportEngineProperties engineProperties,
            PoiSpreadsheetTemplateEngine poiEngine,
            YargReportService yargReportService
    ) {
        this.engineProperties = engineProperties;
        this.poiEngine = poiEngine;
        this.yargReportService = yargReportService;
    }

    public void validate(byte[] content, String format) {
        if (usePoi(format)) {
            poiEngine.validate(content, format);
        } else {
            yargReportService.validateTemplate(content, format);
        }
    }

    public boolean usePoi(String templateFormat) {
        return engineProperties.usePoiForSpreadsheet() && poiEngine.supportsTemplateFormat(templateFormat);
    }

    public TemplateExportResult fillSpreadsheet(
            ReportService.ReportView report,
            ReportTemplateStore.StoredTemplate template,
            List<Map<String, Object>> rows,
            ReportExportFormat outputFormat
    ) {
        if (!usePoi(template.format())) {
            throw new IllegalStateException("POI spreadsheet engine is not selected");
        }
        return poiEngine.fill(report, template, rows, outputFormat);
    }

    public TemplateExportResult fillWithYarg(String path, ReportExportFormat format, Map<String, Object> parameters) {
        YargReportService.ExportedReport exported = yargReportService.export(path, format, parameters);
        return new TemplateExportResult(exported.content(), exported.filename(), exported.contentType());
    }
}
