package com.ispf.server.spi;

/** Why a workflow instance was started. Tags match the automation metrics series. */
public enum WorkflowStartTrigger {
    VARIABLE("variable"),
    CORRELATOR("correlator"),
    EVENT("event"),
    MANUAL("manual");

    private final String tag;

    WorkflowStartTrigger(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
