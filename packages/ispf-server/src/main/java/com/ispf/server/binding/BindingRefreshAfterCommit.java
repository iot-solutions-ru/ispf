package com.ispf.server.binding;

import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.spi.WorkflowBindingRefresh;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.function.ApplicationFunctionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers SQL binding refresh (and downstream alert/correlator actions) until the
 * function transaction commits, so workflow steps see committed application data.
 */
@Component
public class BindingRefreshAfterCommit implements WorkflowBindingRefresh {

    private final SqlBindingObjectService sqlBindingObjectService;
    private final ApplicationSqlBindingService applicationSqlBindingService;
    private final ApplicationFunctionStore applicationFunctionStore;
    private final ApplicationSchemaSession schemaSession;

    public BindingRefreshAfterCommit(
            SqlBindingObjectService sqlBindingObjectService,
            ApplicationSqlBindingService applicationSqlBindingService,
            ApplicationFunctionStore applicationFunctionStore,
            ApplicationSchemaSession schemaSession
    ) {
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.applicationSqlBindingService = applicationSqlBindingService;
        this.applicationFunctionStore = applicationFunctionStore;
        this.schemaSession = schemaSession;
    }

    public void scheduleRefreshAfterFunction(String objectPath, String functionName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refreshCommitted(objectPath, functionName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshCommitted(objectPath, functionName);
            }
        });
    }

    /** Workflow step: runs inside the step's transaction, before it commits. */
    @Override
    public void refreshNow(String objectPath, String functionName) {
        schemaSession.runWithPlatformCatalog(() -> {
            sqlBindingObjectService.refreshAfterFunction(objectPath, functionName);
            applicationFunctionStore.findLatest(objectPath, functionName)
                    .ifPresent(deployed -> applicationSqlBindingService.refreshAfterFunction(
                            deployed.appId(),
                            objectPath,
                            functionName
                    ));
        });
    }

    /** The function has committed: a failing binding must not throw into its caller or skip the other bindings. */
    private void refreshCommitted(String objectPath, String functionName) {
        schemaSession.runWithPlatformCatalog(() -> {
            sqlBindingObjectService.refreshAfterFunctionCommit(objectPath, functionName);
            applicationFunctionStore.findLatest(objectPath, functionName)
                    .ifPresent(deployed -> applicationSqlBindingService.refreshAfterFunctionCommit(
                            deployed.appId(),
                            objectPath,
                            functionName
                    ));
        });
    }
}
