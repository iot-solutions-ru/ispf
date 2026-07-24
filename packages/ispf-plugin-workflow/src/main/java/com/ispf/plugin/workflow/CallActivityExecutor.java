package com.ispf.plugin.workflow;

import java.util.Map;

/**
 * Starts a child workflow for BPMN callActivity and reports terminal/waiting status.
 */
@FunctionalInterface
public interface CallActivityExecutor {

    record Result(
            InstanceStatus status,
            String childInstanceId,
            Map<String, String> variables,
            String errorMessage
    ) {
    }

    Result execute(CallActivityDefinition call, WorkflowInstance parent) throws WorkflowException;
}
