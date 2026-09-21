package com.ispf.server.application.bundle;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured validation issue (ADR-0051 / ADR-0060).
 */
public record BundleValidationIssue(
        String severity,
        String code,
        String path,
        String message,
        String hint,
        String docRef
) {
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    public static final String DOC_LOGIC_HOST =
            "docs/en/application-principles.md#logic-objects-vs-device-mandatory";
    public static final String DOC_BUNDLE =
            "docs/en/solution-developer-public-api.md";

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", severity);
        map.put("code", code != null ? code : "");
        map.put("path", path != null ? path : "");
        map.put("message", message != null ? message : "");
        map.put("hint", hint != null ? hint : "");
        map.put("docRef", docRef != null ? docRef : "");
        return map;
    }

    public static BundleValidationIssue error(String code, String path, String message, String hint, String docRef) {
        return new BundleValidationIssue(ERROR, code, path, message, hint, docRef);
    }

    public static BundleValidationIssue warning(String code, String path, String message, String hint, String docRef) {
        return new BundleValidationIssue(WARNING, code, path, message, hint, docRef);
    }
}
