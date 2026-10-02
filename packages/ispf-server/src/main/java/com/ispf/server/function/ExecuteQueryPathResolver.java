package com.ispf.server.function;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.datasource.DataSourceFunctionSupport;
import com.ispf.server.object.ObjectManager;

import java.util.Optional;

/**
 * Resolves which DATA_SOURCE object path {@link DataSourceFunctionSupport#EXECUTE_QUERY_FUNCTION_NAME}
 * runs against: descriptor {@code dataSourcePath} when set, otherwise the function's host when it is a DATA_SOURCE.
 */
public final class ExecuteQueryPathResolver {

    private ExecuteQueryPathResolver() {
    }

    public static Optional<String> tryResolve(
            ObjectManager objectManager,
            String functionObjectPath,
            FunctionDescriptor descriptor
    ) {
        if (descriptor == null) {
            return Optional.empty();
        }
        String configured = descriptor.dataSourcePath();
        if (configured != null && !configured.isBlank()) {
            String path = configured.trim();
            PlatformObject ds = objectManager.tree().findByPath(path).orElse(null);
            if (ds == null || ds.type() != ObjectType.DATA_SOURCE) {
                return Optional.empty();
            }
            return Optional.of(path);
        }
        PlatformObject node = objectManager.tree().findByPath(functionObjectPath).orElse(null);
        if (node != null && node.type() == ObjectType.DATA_SOURCE) {
            return Optional.of(functionObjectPath);
        }
        return Optional.empty();
    }

    public static String requireResolve(
            ObjectManager objectManager,
            String functionObjectPath,
            FunctionDescriptor descriptor
    ) {
        return tryResolve(objectManager, functionObjectPath, descriptor)
                .orElseThrow(() -> new IllegalArgumentException(
                        "dataSourcePath is required for executeQuery when the function is not on a DATA_SOURCE object"));
    }
}
