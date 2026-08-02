package com.ispf.server.report;

public enum ReportExportFormat {
    CSV,
    PDF,
    XLSX,
    XLS,
    HTML,
    DOCX;

    public static ReportExportFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return CSV;
        }
        return ReportExportFormat.valueOf(value.trim().toUpperCase());
    }

    public String fileExtension() {
        return switch (this) {
            case PDF -> "pdf";
            case XLSX -> "xlsx";
            case XLS -> "xls";
            case HTML -> "html";
            case DOCX -> "docx";
            case CSV -> "csv";
        };
    }

    public String contentType() {
        return switch (this) {
            case PDF -> "application/pdf";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case XLS -> "application/vnd.ms-excel";
            case HTML -> "text/html; charset=UTF-8";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case CSV -> "text/csv; charset=UTF-8";
        };
    }
}
