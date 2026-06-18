package com.ispf.plugin.workflow;

import java.util.Map;

public record UserTaskDefinition(
        String id,
        String name,
        String title,
        String instructions,
        String assigneeRole,
        Map<String, String> parameters
) {
}
