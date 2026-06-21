package com.ispf.server.report;

import tools.jackson.databind.ObjectMapper;
import com.haulmont.yarg.formatters.factory.DefaultFormatterFactory;
import com.haulmont.yarg.loaders.factory.DefaultLoaderFactory;
import com.haulmont.yarg.loaders.impl.JsonDataLoader;
import com.haulmont.yarg.reporting.ReportOutputDocument;
import com.haulmont.yarg.reporting.Reporting;
import com.haulmont.yarg.reporting.RunParams;
import com.haulmont.yarg.structure.Report;
import com.haulmont.yarg.structure.ReportOutputType;
import com.haulmont.yarg.structure.impl.BandBuilder;
import com.haulmont.yarg.structure.impl.ReportBuilder;
import com.haulmont.yarg.structure.impl.ReportTemplateBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class YargReportService {

    private static final String TEMPLATE_CODE = "DEFAULT";
    private static final String DATA_PARAM = "reportData";
    private static final String MAIN_BAND = "Band1";

    private final ReportService reportService;
    private final ReportTemplateStore templateStore;
    private final ObjectMapper objectMapper;
    private final Reporting reporting;

    public YargReportService(
            ReportService reportService,
            ReportTemplateStore templateStore,
            ObjectMapper objectMapper
    ) {
        this.reportService = reportService;
        this.templateStore = templateStore;
        this.objectMapper = objectMapper;
        this.reporting = createReporting();
    }

    public ExportedReport export(String path, ReportExportFormat format, Map<String, Object> parameters) {
        if (format == ReportExportFormat.CSV) {
            throw new IllegalArgumentException("Use CSV export via ReportService");
        }
        ReportService.ReportView report = reportService.getReport(path);
        ReportTemplateStore.StoredTemplate template = templateStore.find(path)
                .orElseThrow(() -> new IllegalArgumentException("Template not configured for report: " + path));

        Map<String, Object> runResult = reportService.run(path, parameters);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) runResult.get("rows");

        try {
            String jsonData = objectMapper.writeValueAsString(Map.of("rows", yargRows(rows)));
            Report yargReport = buildReport(report, template, format);
            RunParams runParams = new RunParams(yargReport)
                    .templateCode(TEMPLATE_CODE)
                    .output(format.toYargOutputType())
                    .param(DATA_PARAM, jsonData)
                    .param("title", report.title())
                    .outputNamePattern(safeFileName(report.title()) + "." + format.fileExtension());

            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                runParams.param(entry.getKey(), entry.getValue());
            }

            ReportOutputDocument document = reporting.runReport(runParams);
            byte[] content = document.getContent();
            if (content == null || content.length == 0) {
                throw new IllegalStateException("YARG produced empty document");
            }
            String filename = document.getDocumentName() != null
                    ? document.getDocumentName()
                    : ReportService.reportIdFromPath(path) + "." + format.fileExtension();
            return new ExportedReport(content, filename, format.contentType());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Report export failed: " + ex.getMessage(), ex);
        }
    }

    private Report buildReport(
            ReportService.ReportView report,
            ReportTemplateStore.StoredTemplate template,
            ReportExportFormat exportFormat
    ) throws Exception {
        ReportOutputType templateOutputType = templateOutputType(template.format());
        String documentName = ReportService.reportIdFromPath(report.path()) + "." + template.format();

        var reportTemplate = new ReportTemplateBuilder()
                .code(TEMPLATE_CODE)
                .documentName(documentName)
                .documentPath(documentName)
                .documentContent(template.content())
                .outputType(templateOutputType)
                .outputNamePattern(safeFileName(report.title()) + "." + exportFormat.fileExtension())
                .build();

        return new ReportBuilder()
                .name(report.title())
                .band(new BandBuilder()
                        .name(MAIN_BAND)
                        .query("", "parameter=" + DATA_PARAM + " $.rows", "json")
                        .build())
                .template(reportTemplate)
                .build();
    }

    private static ReportOutputType templateOutputType(String format) {
        return switch (format.toLowerCase()) {
            case "xlsx" -> ReportOutputType.xlsx;
            case "xls" -> ReportOutputType.xls;
            case "docx" -> ReportOutputType.docx;
            case "doc" -> ReportOutputType.doc;
            case "html" -> ReportOutputType.html;
            default -> throw new IllegalArgumentException("Unsupported template format: " + format);
        };
    }

    private static Reporting createReporting() {
        Reporting reporting = new Reporting();
        reporting.setFormatterFactory(new DefaultFormatterFactory());
        reporting.setLoaderFactory(new DefaultLoaderFactory().setJsonDataLoader(new JsonDataLoader()));
        return reporting;
    }

    private static String safeFileName(String title) {
        if (title == null || title.isBlank()) {
            return "report";
        }
        String sanitized = title.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        return sanitized.isEmpty() ? "report" : sanitized;
    }

    private static List<Map<String, Object>> yargRows(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> mapped = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                mapped.put(entry.getKey().toUpperCase(), entry.getValue());
            }
            return mapped;
        }).toList();
    }

    public record ExportedReport(byte[] content, String filename, String contentType) {
    }
}
