package com.ispf.server.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReportRowPreparation {

    private ReportRowPreparation() {
    }

    static List<Map<String, Object>> prepareRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(row -> {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String upper = entry.getKey().toUpperCase();
                Object value = entry.getValue();
                mapped.put(upper, value);
                mapped.put(upper.toLowerCase(), value);
            }
            return mapped;
        }).toList();
    }
}
