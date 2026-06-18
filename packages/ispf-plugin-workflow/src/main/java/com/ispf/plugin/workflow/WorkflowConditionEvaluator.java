package com.ispf.plugin.workflow;

/**
 * Evaluates BPMN sequence flow condition expressions.
 */
@FunctionalInterface
public interface WorkflowConditionEvaluator {

    boolean evaluate(String expression) throws WorkflowException;
}
