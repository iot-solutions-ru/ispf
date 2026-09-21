package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standard error envelope for platform agent tool results (ADR-0051 / ADR-0060).
 */
public final class AgentToolErrors {

    public static final String DOC_REF_0051 = "decisions/0051-poka-yoke-constraints-over-guards.md";
    public static final String DOC_REF_0060 = "decisions/0060-solution-authoring-constraints.md";

    private AgentToolErrors() {
    }

    public static Map<String, Object> error(String code, String message, String path, String hint) {
        return error(code, message, path, hint, DOC_REF_0051);
    }

    public static Map<String, Object> error(
            String code,
            String message,
            String path,
            String hint,
            String docRef
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ERROR");
        map.put("error", message != null ? message : "");
        map.put("code", code != null ? code : "");
        map.put("path", path != null ? path : "");
        map.put("hint", hint != null ? hint : "");
        map.put("docRef", docRef != null && !docRef.isBlank() ? docRef : DOC_REF_0051);
        return map;
    }
}
