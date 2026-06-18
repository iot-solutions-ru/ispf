package com.ispf.server.function;

import com.ispf.core.model.DataRecord;

public interface FunctionHandler {

    boolean supports(String objectPath, String functionName);

    DataRecord invoke(String objectPath, String functionName, DataRecord input);
}
