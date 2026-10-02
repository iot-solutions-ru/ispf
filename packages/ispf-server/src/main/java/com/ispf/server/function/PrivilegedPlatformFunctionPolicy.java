package com.ispf.server.function;

import com.ispf.server.datasource.DataSourceFunctionSupport;
import org.springframework.stereotype.Component;

/**
 * Platform builtins that must not be invoked directly by operators — only from trusted
 * script chains (nested invoke) or system tasks (workflow, scheduler).
 */
@Component
public class PrivilegedPlatformFunctionPolicy {

    public boolean isScriptOnly(String objectPath, String functionName) {
        return DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME.equals(functionName);
    }
}
