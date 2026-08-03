package com.ispf.server.report;

import com.ispf.server.config.ReportEngineProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Routes Band1 templates to the POI spreadsheet engine (ADR-0053).
 */
@Service
public class ReportTemplateRouter {

    private final ReportEngineProperties engineProperties;
    private final PoiSpreadsheetTemplateEngine poiEngine;

    public ReportTemplateRouter(
            ReportEngineProperties engineProperties,
            PoiSpreadsheetTemplateEngine poiEngine
    ) {
        this.engineProperties = engineProperties;
        this.poiEngine = poiEngine;
    }

    public void validate(byte[] content, String format) {
        if (!poiEngine.supportsTemplateFormat(format)) {
            throw new IllegalArgumentException(
                    "Only xls/xlsx spreadsheet Band1 templates are supported. "
                            + "Use XLSX Band1 templates for templated exports."
            );
        }
        if (!usePoi(format)) {
            throw new IllegalArgumentException(
                    "Report template engine '" + engineProperties.getTemplateEngine()
                            + "' is not available. Configure ispf.reports.template-engine=poi."
            );
        }
        poiEngine.validate(content, format);
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
}
