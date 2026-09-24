package com.ispf.server.spi;

/** Refreshes SQL bindings after a workflow function invoke. */
public interface WorkflowBindingRefresh {

    void refreshNow(String objectPath, String functionName);
}
