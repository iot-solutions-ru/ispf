package com.ispf.plugin.workflow;

import java.util.List;

/**
 * Embedded BPMN subprocess container (BL-176 / ADR-0047).
 */
public record SubProcessDefinition(
        String id,
        String name,
        String startNodeId,
        List<String> endNodeIds
) {}
