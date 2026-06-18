package com.ispf.plugin.workflow;

import java.util.Map;

public record MessageTaskDefinition(
        String id,
        String name,
        String subject,
        String message,
        String channel,
        Map<String, String> parameters
) {
}
