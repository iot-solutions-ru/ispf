package com.ispf.server.platform.analytics.pack;

import java.util.List;
import java.util.Map;

/**
 * Parsed {@code analytics-pack.json} for drop-in Tier C packs (BL-216).
 */
public record AnalyticsPackManifest(
        String packId,
        String version,
        String licenseType,
        String minPlatformVersion,
        String jarFile,
        List<String> functions
) {
    @SuppressWarnings("unchecked")
    static AnalyticsPackManifest fromMap(Map<String, Object> root) {
        if (root == null) {
            return null;
        }
        String packId = stringValue(root.get("packId"));
        String jarFile = stringValue(root.get("jarFile"));
        if (packId.isBlank() || jarFile.isBlank()) {
            return null;
        }
        List<String> functions = List.of();
        Object rawFunctions = root.get("functions");
        if (rawFunctions instanceof List<?> list) {
            functions = list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank()).toList();
        }
        return new AnalyticsPackManifest(
                packId,
                stringValue(root.get("version")),
                stringValue(root.get("licenseType")),
                stringValue(root.get("minPlatformVersion")),
                jarFile,
                functions
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
