package com.ispf.server.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportExportService {

    private final ReportService reportService;
    private final YargReportService yargReportService;
    private final ReportTemplateStore templateStore;
    private final LibreOfficeDocumentConverter libreOfficeDocumentConverter;
    private final ReportTemplateRouter templateRouter;

    public ReportExportService(
            ReportService reportService,
            YargReportService yargReportService,
            ReportTemplateStore templateStore,
            LibreOfficeDocumentConverter libreOfficeDocumentConverter,
            ReportTemplateRouter templateRouter
    ) {
        this.reportService = reportService;
        this.yargReportService = yargReportService;
        this.templateStore = templateStore;
        this.libreOfficeDocumentConverter = libreOfficeDocumentConverter;
        this.templateRouter = templateRouter;
    }

    @Transactional(readOnly = true)
    public ExportedFile export(String path, ReportExportFormat format, Map<String, Object> parameters) {
        return switch (format) {
            case CSV -> csv(path, parameters);
            case HTML -> exportHtml(path, parameters);
            case XLSX -> exportSpreadsheet(path, parameters, ReportExportFormat.XLSX);
            case XLS -> exportSpreadsheet(path, parameters, ReportExportFormat.XLS);
            case PDF -> exportPdf(path, parameters);
            case DOCX -> exportTemplated(path, ReportExportFormat.DOCX, parameters);
        };
    }

    private ExportedFile csv(String path, Map<String, Object> parameters) {
        byte[] content = reportService.exportCsv(path, parameters);
        return new ExportedFile(
                content,
                ReportService.reportIdFromPath(path) + ".csv",
                ReportExportFormat.CSV.contentType()
        );
    }

    private ExportedFile exportHtml(String path, Map<String, Object> parameters) {
        if (reportService.hasTemplate(path)) {
            try {
                return exportTemplated(path, ReportExportFormat.HTML, parameters);
            } catch (IllegalArgumentException ignored) {
                // fall through to table HTML
            }
        }
        return table(path, parameters, ReportExportFormat.HTML);
    }

    private ExportedFile table(String path, Map<String, Object> parameters, ReportExportFormat format) {
        byte[] content = format == ReportExportFormat.HTML
                ? reportService.exportHtmlTable(path, parameters)
                : format == ReportExportFormat.XLS
                ? reportService.exportXlsTable(path, parameters)
                : reportService.exportXlsxTable(path, parameters);
        return new ExportedFile(
                content,
                ReportService.reportIdFromPath(path) + "." + format.fileExtension(),
                format.contentType()
        );
    }

    private ExportedFile exportSpreadsheet(String path, Map<String, Object> parameters, ReportExportFormat targetFormat) {
        Optional<ReportTemplateStore.StoredTemplate> template = templateStore.find(path);
        if (template.isEmpty()) {
            return table(path, parameters, targetFormat);
        }

        String templateFormat = template.get().format().toLowerCase();
        if (!"xls".equals(templateFormat) && !"xlsx".equals(templateFormat)) {
            return table(path, parameters, targetFormat);
        }

        ReportExportFormat nativeFormat = "xls".equals(templateFormat)
                ? ReportExportFormat.XLS
                : ReportExportFormat.XLSX;

        try {
            Map<String, Object> runResult = reportService.run(path, parameters);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) runResult.get("rows");

            byte[] content;
            String filename;
            String contentType;

            if (templateRouter.usePoi(templateFormat)) {
                ReportService.ReportView report = reportService.getReport(path);
                TemplateExportResult filled = templateRouter.fillSpreadsheet(
                        report, template.get(), rows, nativeFormat
                );
                content = filled.content();
                filename = filled.filename();
                contentType = filled.contentType();
            } else {
                YargReportService.ExportedReport exported =
                        yargReportService.export(path, nativeFormat, parameters);
                content = exported.content();
                filename = exported.filename();
                contentType = exported.contentType();
            }

            if (targetFormat == ReportExportFormat.XLSX && nativeFormat == ReportExportFormat.XLS) {
                content = libreOfficeDocumentConverter.convertSpreadsheet(content, "xls", "xlsx");
                filename = ReportService.reportIdFromPath(path) + ".xlsx";
                contentType = ReportExportFormat.XLSX.contentType();
            } else if (targetFormat == ReportExportFormat.XLS && nativeFormat == ReportExportFormat.XLSX) {
                content = libreOfficeDocumentConverter.convertSpreadsheet(content, "xlsx", "xls");
                filename = ReportService.reportIdFromPath(path) + ".xls";
                contentType = ReportExportFormat.XLS.contentType();
            } else if (targetFormat == ReportExportFormat.XLSX) {
                filename = ReportService.reportIdFromPath(path) + ".xlsx";
                contentType = ReportExportFormat.XLSX.contentType();
            }

            // XLSX is a ZIP; UTF-8 substring search is unreliable. Guard applies to .xls (BIFF).
            if (nativeFormat == ReportExportFormat.XLS
                    && YargExportContentGuard.outputMissingReportData(content, runResult)) {
                return table(path, parameters, targetFormat);
            }

            return new ExportedFile(content, filename, contentType);
        } catch (IllegalArgumentException ex) {
            if (YargReportingSupport.isLibreOfficeRequiredError(ex.getMessage())) {
                throw ex;
            }
            return table(path, parameters, targetFormat);
        }
    }

    private ExportedFile exportPdf(String path, Map<String, Object> parameters) {
        Optional<ReportTemplateStore.StoredTemplate> template = templateStore.find(path);
        if (template.isEmpty()) {
            return pdfFromTable(path, parameters);
        }

        String templateFormat = template.get().format().toLowerCase();
        if ("xlsx".equals(templateFormat) || "xls".equals(templateFormat)) {
            return exportPdfFromExcelTemplate(path, parameters, template.get());
        }

        try {
            return exportTemplated(path, ReportExportFormat.PDF, parameters);
        } catch (IllegalArgumentException ex) {
            if (YargReportingSupport.isLibreOfficeRequiredError(ex.getMessage())) {
                throw ex;
            }
            return pdfFromTable(path, parameters);
        }
    }

    private ExportedFile exportPdfFromExcelTemplate(
            String path,
            Map<String, Object> parameters,
            ReportTemplateStore.StoredTemplate template
    ) {
        String templateFormat = template.format().toLowerCase();
        try {
            Map<String, Object> runResult = reportService.run(path, parameters);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) runResult.get("rows");

            byte[] spreadsheet;
            String loFormat = templateFormat;
            ReportExportFormat nativeFormat = "xls".equals(templateFormat)
                    ? ReportExportFormat.XLS
                    : ReportExportFormat.XLSX;

            if (templateRouter.usePoi(templateFormat)) {
                ReportService.ReportView report = reportService.getReport(path);
                TemplateExportResult filled = templateRouter.fillSpreadsheet(
                        report, template, rows, nativeFormat
                );
                spreadsheet = filled.content();
            } else {
                YargReportService.ExportedReport exported =
                        yargReportService.export(path, nativeFormat, parameters);
                spreadsheet = exported.content();
                if (nativeFormat == ReportExportFormat.XLS
                        && YargExportContentGuard.outputMissingReportData(spreadsheet, runResult)) {
                    return pdfFromTable(path, parameters);
                }
            }

            if ("xls".equals(loFormat)) {
                spreadsheet = libreOfficeDocumentConverter.convertSpreadsheet(spreadsheet, "xls", "xlsx");
                loFormat = "xlsx";
            }
            byte[] pdf = libreOfficeDocumentConverter.convertSpreadsheetToPdf(spreadsheet, loFormat);
            return new ExportedFile(
                    pdf,
                    ReportService.reportIdFromPath(path) + ".pdf",
                    ReportExportFormat.PDF.contentType()
            );
        } catch (IllegalArgumentException ex) {
            if (YargReportingSupport.isLibreOfficeRequiredError(ex.getMessage())) {
                throw ex;
            }
        }
        return pdfFromTable(path, parameters);
    }

    private ExportedFile exportTemplated(String path, ReportExportFormat format, Map<String, Object> parameters) {
        if (!reportService.hasTemplate(path)) {
            throw new IllegalArgumentException(
                    "Export format " + format.fileExtension().toUpperCase()
                            + " requires a report template. Upload via Report Builder → Report template."
            );
        }
        YargReportService.ExportedReport exported = yargReportService.export(path, format, parameters);
        if (YargExportContentGuard.shouldValidate(format)) {
            Map<String, Object> runResult = reportService.run(path, parameters);
            if (YargExportContentGuard.outputMissingReportData(exported.content(), runResult)) {
                throw new IllegalArgumentException(
                        "Template did not receive report data — check ${Band1.FIELD} placeholders "
                                + "match report columns in UPPERCASE."
                );
            }
        }
        return new ExportedFile(exported.content(), exported.filename(), exported.contentType());
    }

    private ExportedFile pdfFromTable(String path, Map<String, Object> parameters) {
        byte[] xlsx = reportService.exportXlsxTable(path, parameters);
        byte[] pdf = libreOfficeDocumentConverter.convertSpreadsheetToPdf(xlsx, "xlsx");
        return new ExportedFile(
                pdf,
                ReportService.reportIdFromPath(path) + ".pdf",
                ReportExportFormat.PDF.contentType()
        );
    }

    public record ExportedFile(byte[] content, String filename, String contentType) {
    }
}
