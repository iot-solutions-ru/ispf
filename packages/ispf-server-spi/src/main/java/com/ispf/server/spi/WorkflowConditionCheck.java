package com.ispf.server.spi;

/** Formal check of a workflow sequence-flow condition before it is stored. */
public interface WorkflowConditionCheck {

    void requireSafeCondition(String expression);
}
