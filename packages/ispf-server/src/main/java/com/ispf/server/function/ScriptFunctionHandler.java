package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.script.FunctionScriptEngine;
import com.ispf.server.application.script.ScriptExecutionContext;
import com.ispf.server.binding.BindingRefreshAfterCommit;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@Order(-1)
public class ScriptFunctionHandler implements FunctionHandler {

    private final ObjectManager objectManager;
    private final FunctionScriptEngine scriptEngine;
    private final ApplicationSchemaSession schemaSession;
    private final DataSourceSqlSession dataSourceSqlSession;
    private final FunctionService functionService;
    private final BindingRefreshAfterCommit bindingRefreshAfterCommit;

    public ScriptFunctionHandler(
            ObjectManager objectManager,
            FunctionScriptEngine scriptEngine,
            ApplicationSchemaSession schemaSession,
            DataSourceSqlSession dataSourceSqlSession,
            @Lazy FunctionService functionService,
            BindingRefreshAfterCommit bindingRefreshAfterCommit
    ) {
        this.objectManager = objectManager;
        this.scriptEngine = scriptEngine;
        this.schemaSession = schemaSession;
        this.dataSourceSqlSession = dataSourceSqlSession;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        FunctionDescriptor descriptor = schemaSession.callWithPlatformCatalog(() -> {
            PlatformObject node = objectManager.require(objectPath);
            FunctionDescriptor fn = node.functions().get(functionName);
            if (fn == null || !fn.hasScriptBody()) {
                throw new IllegalStateException("Script function not found: " + functionName);
            }
            return fn;
        });
        String dataSourcePath = descriptor.dataSourcePath();
        DataRecord[] outputHolder = new DataRecord[1];
        Runnable execute = () -> outputHolder[0] = scriptEngine.execute(
                descriptor.sourceBody(),
                input,
                descriptor.outputSchema(),
                nestedContext(objectPath, functionName, 0)
        );
        if (dataSourcePath != null && !dataSourcePath.isBlank()) {
            dataSourceSqlSession.runWithDataSource(dataSourcePath, ignored -> execute.run());
        } else {
            schemaSession.runWithPlatformCatalog(execute);
        }
        bindingRefreshAfterCommit.scheduleRefreshAfterFunction(objectPath, functionName);
        return outputHolder[0];
    }

    private ScriptExecutionContext nestedContext(String objectPath, String functionName, int depth) {
        return new ScriptExecutionContext() {
            @Override
            public String callerObjectPath() {
                return objectPath;
            }

            @Override
            public DataRecord invokeFunction(String nestedPath, String nestedName, Map<String, Object> nestedInput) {
                if (depth >= ScriptExecutionContext.MAX_CALL_DEPTH) {
                    throw new IllegalStateException("Script function call depth exceeded");
                }
                DataRecord nestedRecord = nestedInput != null
                        ? schemaSession.callWithPlatformCatalog(() -> DataRecord.single(
                                objectManager.require(nestedPath).functions().get(nestedName).inputSchema(),
                                nestedInput
                        ))
                        : null;
                return FunctionInvocationScope.callNested(
                        () -> functionService.invoke(nestedPath, nestedName, nestedRecord));
            }
        };
    }
}
