package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.script.FunctionScriptEngine;
import com.ispf.server.application.script.ScriptExecutionContext;
import com.ispf.server.binding.BindingRefreshAfterCommit;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(-1)
public class ScriptFunctionHandler implements FunctionHandler {

    private final ObjectManager objectManager;
    private final FunctionScriptEngine scriptEngine;
    private final ApplicationSchemaSession schemaSession;
    private final DataSourcePathResolver dataSourcePathResolver;
    private final FunctionService functionService;
    private final BindingRefreshAfterCommit bindingRefreshAfterCommit;

    public ScriptFunctionHandler(
            ObjectManager objectManager,
            FunctionScriptEngine scriptEngine,
            ApplicationSchemaSession schemaSession,
            DataSourcePathResolver dataSourcePathResolver,
            @Lazy FunctionService functionService,
            BindingRefreshAfterCommit bindingRefreshAfterCommit
    ) {
        this.objectManager = objectManager;
        this.scriptEngine = scriptEngine;
        this.schemaSession = schemaSession;
        this.dataSourcePathResolver = dataSourcePathResolver;
        this.functionService = functionService;
        this.bindingRefreshAfterCommit = bindingRefreshAfterCommit;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return false;
        }
        FunctionDescriptor descriptor = node.functions().get(functionName);
        return descriptor != null && descriptor.hasScriptBody();
    }

    @Override
    @Transactional
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        FunctionDescriptor descriptor = schemaSession.callWithPlatformCatalog(() -> {
            PlatformObject node = objectManager.require(objectPath);
            FunctionDescriptor fn = node.functions().get(functionName);
            if (fn == null || !fn.hasScriptBody()) {
                throw new IllegalStateException("Script function not found: " + functionName);
            }
            return fn;
        });
        String schemaName = descriptor.dataSourcePath() != null && !descriptor.dataSourcePath().isBlank()
                ? dataSourcePathResolver.resolveSchemaName(descriptor.dataSourcePath())
                : null;
        DataRecord[] outputHolder = new DataRecord[1];
        Runnable execute = () -> outputHolder[0] = scriptEngine.execute(
                descriptor.sourceBody(),
                input,
                descriptor.outputSchema(),
                nestedContext(objectPath, functionName, 0)
        );
        if (schemaName != null) {
            schemaSession.runInSchema(schemaName, execute);
        } else {
            schemaSession.runWithPlatformCatalog(execute);
        }
        bindingRefreshAfterCommit.scheduleRefreshAfterFunction(objectPath, functionName);
        return outputHolder[0];
    }

    private ScriptExecutionContext nestedContext(String objectPath, String functionName, int depth) {
        return (nestedPath, nestedName, nestedInput) -> {
            if (depth >= ScriptExecutionContext.MAX_CALL_DEPTH) {
                throw new IllegalStateException("Script function call depth exceeded");
            }
            DataRecord nestedRecord = nestedInput != null
                    ? schemaSession.callWithPlatformCatalog(() -> DataRecord.single(
                            objectManager.require(nestedPath).functions().get(nestedName).inputSchema(),
                            nestedInput
                    ))
                    : null;
            return functionService.invoke(nestedPath, nestedName, nestedRecord);
        };
    }
}
