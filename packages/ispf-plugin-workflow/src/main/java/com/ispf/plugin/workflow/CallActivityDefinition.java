package com.ispf.plugin.workflow;

import java.util.Map;

/**
 * BPMN callActivity — start another WORKFLOW and wait for it (ISPF subset).
 */
public record CallActivityDefinition(
        String id,
        String name,
        String workflowPath,
        Map<String, String> parameters
) {
}
