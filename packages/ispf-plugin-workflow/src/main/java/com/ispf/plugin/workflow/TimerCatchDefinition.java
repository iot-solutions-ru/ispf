package com.ispf.plugin.workflow;

import java.util.Map;

public record TimerCatchDefinition(
        String id,
        String name,
        int durationSeconds,
        Map<String, String> parameters
) {
}
