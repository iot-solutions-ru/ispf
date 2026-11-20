package com.ispf.server.workflow;

/**
 * Missing workflow run / instance id (not an object-tree path).
 * Mapped to HTTP 404 by {@code ApiExceptionHandler}.
 */
public final class WorkflowInstanceNotFoundException extends RuntimeException {

    private final String instanceId;

    public WorkflowInstanceNotFoundException(String instanceId) {
        super("Workflow instance not found: " + instanceId);
        this.instanceId = instanceId;
    }

    public String instanceId() {
        return instanceId;
    }
}
