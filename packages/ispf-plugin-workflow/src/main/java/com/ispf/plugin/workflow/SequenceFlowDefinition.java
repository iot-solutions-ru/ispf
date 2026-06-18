package com.ispf.plugin.workflow;

/**
 * Directed edge in a BPMN process with optional condition expression.
 */
public record SequenceFlowDefinition(
        String id,
        String sourceRef,
        String targetRef,
        String conditionExpression,
        boolean defaultFlow
) {
}
