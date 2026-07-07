package com.ispf.plugin.workflow;

import java.util.Map;

public record BoundaryTimerDefinition(
        String id,
        String name,
        String attachedToRef,
        int durationSeconds,
        boolean interrupting,
        Map<String, String> parameters
) {
}
