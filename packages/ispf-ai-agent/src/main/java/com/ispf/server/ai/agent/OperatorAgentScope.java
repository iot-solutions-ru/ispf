package com.ispf.server.ai.agent;

import java.util.List;

/**
 * Declarative path scope for an operator application (dashboards, reports, manifest screens).
 */
public record OperatorAgentScope(
        String appId,
        String title,
        List<String> pathPrefixes,
        String briefingRoot
) {
    public boolean isPathAllowed(String path) {
        if (path == null || path.isBlank() || pathPrefixes == null || pathPrefixes.isEmpty()) {
            return false;
        }
        String normalized = path.trim();
        for (String prefix : pathPrefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            String p = prefix.trim();
            if (normalized.equals(p) || normalized.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }
}
