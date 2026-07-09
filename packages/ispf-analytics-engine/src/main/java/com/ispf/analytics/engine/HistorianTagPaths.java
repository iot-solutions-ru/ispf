package com.ispf.analytics.engine;

/**
 * Composite analytics tag identity: {@code objectPath#ruleId} (ADR-0041).
 */
public final class HistorianTagPaths {

    public static final char SEPARATOR = '#';

    private HistorianTagPaths() {
    }

    public static String encode(String objectPath, String ruleId) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId is required");
        }
        if (objectPath.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException("objectPath must not contain '#': " + objectPath);
        }
        if (ruleId.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException("ruleId must not contain '#': " + ruleId);
        }
        return objectPath + SEPARATOR + ruleId;
    }

    public static boolean isComposite(String tagPath) {
        return tagPath != null && tagPath.indexOf(SEPARATOR) > 0;
    }

    public static String objectPath(String tagPath) {
        if (tagPath == null || tagPath.isBlank()) {
            return tagPath;
        }
        int idx = tagPath.indexOf(SEPARATOR);
        return idx > 0 ? tagPath.substring(0, idx) : tagPath;
    }

    public static String ruleId(String tagPath) {
        if (tagPath == null) {
            return "";
        }
        int idx = tagPath.indexOf(SEPARATOR);
        return idx > 0 && idx < tagPath.length() - 1 ? tagPath.substring(idx + 1) : "";
    }
}
