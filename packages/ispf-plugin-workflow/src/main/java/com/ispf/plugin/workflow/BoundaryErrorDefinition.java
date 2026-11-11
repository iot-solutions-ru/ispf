package com.ispf.plugin.workflow;

import java.util.List;
import java.util.Map;

/**
 * Error boundary on an activity. A blank {@code errorCode} catches every failure.
 * A non-blank code matches when the exception message contains that code; the longest match wins.
 */
public record BoundaryErrorDefinition(
        String id,
        String name,
        String attachedToRef,
        String errorCode,
        boolean interrupting,
        Map<String, String> parameters
) {
    public static BoundaryErrorDefinition match(List<BoundaryErrorDefinition> boundaries, String message) {
        if (boundaries == null || boundaries.isEmpty()) {
            return null;
        }
        BoundaryErrorDefinition catchAll = null;
        BoundaryErrorDefinition best = null;
        for (BoundaryErrorDefinition boundary : boundaries) {
            String code = boundary.errorCode();
            if (code == null || code.isBlank()) {
                if (catchAll == null) {
                    catchAll = boundary;
                }
                continue;
            }
            if (message != null && message.contains(code)
                    && (best == null || code.length() > best.errorCode().length())) {
                best = boundary;
            }
        }
        return best != null ? best : catchAll;
    }
}
