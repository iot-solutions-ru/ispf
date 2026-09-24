package com.ispf.server.spi;

import java.util.Map;

/** NATS publishes issued by workflow tasks and instance-state changes. */
public interface WorkflowMessageBus {

    void publish(String subject, String message);

    void publishWorkflowEvent(String workflowPath, String event, Map<String, Object> payload);
}
