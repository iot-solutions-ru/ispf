package com.ispf.server.function;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.datasource.DataSourceFunctionSupport;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

/**
 * Platform builtins that must not be invoked directly by operators — only from trusted
 * script chains (nested invoke) or system tasks (workflow, scheduler).
 */
@Component
public class PrivilegedPlatformFunctionPolicy {

    private final ObjectManager objectManager;

    public PrivilegedPlatformFunctionPolicy(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public boolean isScriptOnly(String objectPath, String functionName) {
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return false;
        }
        if (node.type() == ObjectType.DATA_SOURCE
                && DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION_NAME.equals(functionName)) {
            return true;
        }
        return false;
    }
}
