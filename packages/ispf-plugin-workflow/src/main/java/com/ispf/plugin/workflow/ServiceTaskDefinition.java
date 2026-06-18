package com.ispf.plugin.workflow;

import java.util.Map;

public record ServiceTaskDefinition(
        String id,
        String name,
        WorkflowActionType action,
        Map<String, String> parameters
) {
}
