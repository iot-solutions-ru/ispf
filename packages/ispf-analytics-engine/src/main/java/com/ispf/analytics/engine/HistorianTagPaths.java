package com.ispf.analytics.engine;

/**
 * Composite analytics tag identity: {@code objectPath/tag/ruleId} (ADR-0041 / ADR-0043).
 */
public final class HistorianTagPaths {

    public static final String TAG_SEGMENT = "/tag/";

    private HistorianTagPaths() {
    }

    public static String encode(String objectPath, String ruleId) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId is required");
        }
        if (objectPath.contains(TAG_SEGMENT)) {
            throw new IllegalArgumentException("objectPath must not contain '/tag/': " + objectPath);
        }
        if (ruleId.contains("/")) {
            throw new IllegalArgumentException("ruleId must not contain '/': " + ruleId);
        }
        return objectPath + TAG_SEGMENT + ruleId;
    }

    public static boolean isComposite(String tagPath) {
        return tagPath != null && tagPath.contains(TAG_SEGMENT);
    }

    public static String objectPath(String tagPath) {
        if (tagPath == null || tagPath.isBlank()) {
            return tagPath;
        }
        int idx = tagPath.indexOf(TAG_SEGMENT);
        return idx > 0 ? tagPath.substring(0, idx) : tagPath;
    }

    public static String ruleId(String tagPath) {
        if (tagPath == null) {
            return "";
        }
        int idx = tagPath.indexOf(TAG_SEGMENT);
        return idx > 0 && idx + TAG_SEGMENT.length() < tagPath.length()
                ? tagPath.substring(idx + TAG_SEGMENT.length())
                : "";
    }
}
