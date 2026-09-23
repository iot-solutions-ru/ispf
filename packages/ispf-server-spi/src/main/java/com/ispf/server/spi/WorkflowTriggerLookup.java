package com.ispf.server.spi;

/**
 * Whether a workflow trigger is registered for an object event or variable.
 * Implemented by the workflow index so the object tree does not depend on it.
 */
public interface WorkflowTriggerLookup {

    boolean hasEventWorkflows(String objectPath, String eventName);

    boolean hasVariableWorkflows(String objectPath, String variableName);
}
