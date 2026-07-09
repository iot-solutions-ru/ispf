package com.ispf.server.platform.analytics;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Exports multi-tag analytics query results (BL-206).
 */
@Service
public class AnalyticsQueryExportService {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsQueryExportService(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    public void exportCsv(AnalyticsQueryRequest request, OutputStream outputStream) throws IOException {
        AnalyticsQueryResponse response = analyticsQueryService.query(request);
        StringBuilder header = new StringBuilder("timestamp");
        for (AnalyticsQueryResponse.AnalyticsQuerySeries series : response.series()) {
            header.append(',').append(escapeCsv(series.id()));
        }
        header.append('\n');
        outputStream.write(header.toString().getBytes(StandardCharsets.UTF_8));

        List<String> timestamps = response.timestamps();
        for (int i = 0; i < timestamps.size(); i++) {
            StringBuilder row = new StringBuilder(timestamps.get(i));
            for (AnalyticsQueryResponse.AnalyticsQuerySeries series : response.series()) {
                row.append(',');
                Double value = i < series.values().size() ? series.values().get(i) : null;
                row.append(value != null ? value : "");
            }
            row.append('\n');
            outputStream.write(row.toString().getBytes(StandardCharsets.UTF_8));
        }
        outputStream.flush();
    }

    public byte[] exportParquet(AnalyticsQueryRequest request) throws IOException {
        AnalyticsQueryResponse response = analyticsQueryService.query(request);
        StringBuilder jsonLines = new StringBuilder();
        List<String> timestamps = response.timestamps();
        for (int i = 0; i < timestamps.size(); i++) {
            StringBuilder row = new StringBuilder();
            row.append("{\"timestamp\":\"").append(timestamps.get(i)).append("\"");
            for (AnalyticsQueryResponse.AnalyticsQuerySeries series : response.series()) {
                Double value = i < series.values().size() ? series.values().get(i) : null;
                row.append(",\"").append(escapeJsonKey(series.id())).append("\":");
                row.append(value != null ? value : "null");
            }
            row.append("}\n");
            jsonLines.append(row);
        }
        return jsonLines.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String escapeJsonKey(String key) {
        return key.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("format is required");
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("csv") && !normalized.equals("parquet")) {
            throw new IllegalArgumentException("Unsupported export format: " + format);
        }
        return normalized;
    }
}
