package com.ispf.plugin.workflow;

import java.util.List;

/**
 * Embedded BPMN subprocess container (BL-176 stub).
 */
public record SubProcessDefinition(
        String id,
        String name,
        String startNodeId,
        List<String> endNodeIds
) {}
