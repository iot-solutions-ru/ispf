package com.ispf.driver.flexible;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline point mapping: {@code req:...|var:k=v|verifyChecksum|extract:...}.
 */
record FlexExchangePoint(
        String requestTemplate,
        Map<String, String> variables,
        boolean verifyChecksum,
        FlexExtractor extractor
) {

    static boolean isPipeline(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("req:") || trimmed.contains("|extract:");
    }

    static FlexExchangePoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Flexible point mapping is blank");
        }
        String[] segments = raw.trim().split("\\|");
        String requestTemplate = null;
        Map<String, String> variables = new HashMap<>();
        boolean verifyChecksum = false;
        FlexExtractor extractor = null;

        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("req:")) {
                requestTemplate = trimmed.substring("req:".length());
            } else if (trimmed.startsWith("var:")) {
                parseVar(trimmed.substring("var:".length()), variables);
            } else if ("verifyChecksum".equalsIgnoreCase(trimmed)) {
                verifyChecksum = true;
            } else if (trimmed.startsWith("extract:")) {
                extractor = FlexExtractor.parse(trimmed);
            } else {
                throw new IllegalArgumentException("Unknown pipeline segment: " + trimmed);
            }
        }
        if (requestTemplate == null || requestTemplate.isBlank()) {
            throw new IllegalArgumentException("Pipeline mapping requires req: segment");
        }
        if (extractor == null) {
            throw new IllegalArgumentException("Pipeline mapping requires extract: segment");
        }
        return new FlexExchangePoint(
                requestTemplate,
                Collections.unmodifiableMap(variables),
                verifyChecksum,
                extractor
        );
    }

    private static void parseVar(String body, Map<String, String> variables) {
        int eq = body.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("Invalid var segment: " + body);
        }
        variables.put(body.substring(0, eq).trim(), body.substring(eq + 1).trim());
    }

    byte[] renderRequest(Map<String, String> configuration) {
        Map<String, String> merged = new LinkedHashMap<>(configuration);
        merged.putAll(variables);
        return FlexTemplate.render(requestTemplate, merged);
    }

    String requestGroupKey(Map<String, String> configuration) {
        return FlexTemplate.renderKey(requestTemplate, mergeVars(configuration));
    }

    Map<String, String> mergeVars(Map<String, String> configuration) {
        Map<String, String> merged = new LinkedHashMap<>(configuration);
        merged.putAll(variables);
        return merged;
    }

    static Map<String, List<Map.Entry<String, FlexExchangePoint>>> groupByRequest(
            Map<String, String> pointMappings,
            Map<String, String> configuration) {
        Map<String, List<Map.Entry<String, FlexExchangePoint>>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            if (!isPipeline(entry.getValue())) {
                continue;
            }
            FlexExchangePoint point = parse(entry.getValue());
            String key = point.requestGroupKey(configuration);
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(Map.entry(entry.getKey(), point));
        }
        return groups;
    }
}
