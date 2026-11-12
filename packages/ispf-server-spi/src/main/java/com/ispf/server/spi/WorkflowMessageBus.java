package com.ispf.server.spi;

import java.util.Map;

/** NATS publishes issued by workflow tasks and instance-state changes. */
public interface WorkflowMessageBus {

    void publish(String subject, String message);

    /**
     * BPMN NATS delivery: implementations that can detect delivery failure should throw
     * {@link IllegalStateException} so the workflow step can retry or hit an error boundary.
     */
    default void publishWorkflowStep(String subject, String message) {
        publish(subject, message);
    }

    void publishWorkflowEvent(String workflowPath, String event, Map<String, Object> payload);
}
