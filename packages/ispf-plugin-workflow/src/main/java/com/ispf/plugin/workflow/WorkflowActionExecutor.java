package com.ispf.plugin.workflow;

public interface WorkflowActionExecutor {

    void execute(ServiceTaskDefinition task, WorkflowInstance instance) throws WorkflowException;
}
