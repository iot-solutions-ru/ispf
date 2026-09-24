package com.ispf.server.spi;

import com.ispf.core.model.DataRecord;

/** Function invokes issued by a workflow service task. */
public interface WorkflowFunctionCalls {

    DataRecord invoke(String objectPath, String functionName);

    DataRecord invoke(String objectPath, String functionName, DataRecord input);
}
