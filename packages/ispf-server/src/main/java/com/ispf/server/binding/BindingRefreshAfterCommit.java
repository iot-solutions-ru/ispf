package com.ispf.server.binding;

import com.ispf.server.application.binding.ApplicationSqlBindingService;
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
public class BindingRefreshAfterCommit {

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
            refreshNow(objectPath, functionName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshNow(objectPath, functionName);
            }
        });
    }

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
}
