package com.ispf.server.report;

public record ExportedReport(byte[] content, String filename, String contentType) {
}
