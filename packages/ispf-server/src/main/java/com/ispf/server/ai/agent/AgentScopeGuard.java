package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class AgentScopeGuard {

    private static final Set<String> PATH_ARGUMENT_KEYS = Set.of(
            "path",
            "parent",
            "parentPath",
            "objectPath",
            "devicePath",
            "dashboardPath",
            "reportPath",
            "workflowPath"
    );

    private AgentScopeGuard() {
    }

    static void enforceOperatorScope(String toolName, Map<String, Object> arguments, OperatorAgentScope scope) {
        if (scope == null || arguments == null || arguments.isEmpty()) {
            return;
        }
        for (String key : PATH_ARGUMENT_KEYS) {
            Object raw = arguments.get(key);
            if (raw instanceof String path && !path.isBlank() && !scope.isPathAllowed(path)) {
                throw new IllegalArgumentException(
                        "Path outside operator app scope: " + path + " (tool=" + toolName + ", app=" + scope.appId() + ")"
                );
            }
        }
        Object rows = arguments.get("inputRows");
        if (rows instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getValue() instanceof String path
                                && !path.isBlank()
                                && PATH_ARGUMENT_KEYS.contains(String.valueOf(entry.getKey()))
                                && !scope.isPathAllowed(path)) {
                            throw new IllegalArgumentException(
                                    "Path outside operator app scope: " + path + " (tool=" + toolName + ")"
                            );
                        }
                    }
                }
            }
        }
    }
}
