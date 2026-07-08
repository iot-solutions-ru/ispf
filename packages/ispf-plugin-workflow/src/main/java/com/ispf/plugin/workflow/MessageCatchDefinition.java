package com.ispf.plugin.workflow;

import java.util.Map;

/**
 * Parsed BPMN message catch event (BL-176).
 */
public record MessageCatchDefinition(
        String id,
        String name,
        String messageName,
        Map<String, String> parameters
) {
}
