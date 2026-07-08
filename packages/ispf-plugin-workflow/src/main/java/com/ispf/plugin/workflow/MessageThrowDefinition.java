package com.ispf.plugin.workflow;

import java.util.Map;

/**
 * Parsed BPMN intermediate message throw event (BL-176).
 */
public record MessageThrowDefinition(
        String id,
        String name,
        String messageName,
        Map<String, String> parameters
) {
}
