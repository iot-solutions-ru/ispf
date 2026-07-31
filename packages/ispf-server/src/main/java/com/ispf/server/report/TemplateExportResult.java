package com.ispf.server.report;

/**
 * Result of filling a report template (POI or YARG).
 */
public record TemplateExportResult(byte[] content, String filename, String contentType) {
}
