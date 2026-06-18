package com.ispf.plugin.workflow;

public interface MessageTaskExecutor {

    void execute(MessageTaskDefinition task, WorkflowInstance instance) throws WorkflowException;
}
