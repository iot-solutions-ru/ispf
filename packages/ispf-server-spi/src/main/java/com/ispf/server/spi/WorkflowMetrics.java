package com.ispf.server.spi;

/** Records a workflow start for platform metrics. */
public interface WorkflowMetrics {

    void recordWorkflowStart(WorkflowStartTrigger trigger);
}
