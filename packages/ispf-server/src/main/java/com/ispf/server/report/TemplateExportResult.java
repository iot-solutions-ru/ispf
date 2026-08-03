package com.ispf.server.report;

/**
 * Result of filling a report template.
 */
public record TemplateExportResult(byte[] content, String filename, String contentType) {
}
