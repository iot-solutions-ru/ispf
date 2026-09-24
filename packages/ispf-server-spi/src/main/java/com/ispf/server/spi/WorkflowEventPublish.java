package com.ispf.server.spi;

import com.ispf.core.model.DataRecord;

/** Fires an object event from a workflow service task. */
public interface WorkflowEventPublish {

    void publishFired(String objectPath, String eventName, DataRecord payload);
}
