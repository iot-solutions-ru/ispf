package com.ispf.server.binding;

import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import com.ispf.server.object.bus.ObjectChangeEventBus;
import com.ispf.server.spi.WorkflowBindingRefresh;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.function.ApplicationFunctionStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Defers SQL binding refresh (and downstream alert/correlator actions) until the
 * function transaction commits, so workflow steps see committed application data.
 * The refresh runs on the object-change bus, outside the function's transaction, so its values are saved.
 */
@Component
public class BindingRefreshAfterCommit implements WorkflowBindingRefresh, ObjectChangeAsyncHandler {

    private final SqlBindingObjectService sqlBindingObjectService;
    private final ApplicationSqlBindingService applicationSqlBindingService;
    private final ApplicationFunctionStore applicationFunctionStore;
    private final ApplicationSchemaSession schemaSession;
    private final ObjectChangeEventBus eventBus;
    /**
     * Per function, whether it committed again while its bindings were refreshing. One refresh per function at a
     * time: on two workers, the older query could finish last and overwrite the newer value.
     */
    private final ConcurrentHashMap<String, Boolean> committedDuringRefresh = new ConcurrentHashMap<>();

    public BindingRefreshAfterCommit(
            SqlBindingObjectService sqlBindingObjectService,
            ApplicationSqlBindingService applicationSqlBindingService,
            ApplicationFunctionStore applicationFunctionStore,
            ApplicationSchemaSession schemaSession,
            @Lazy ObjectChangeEventBus eventBus
    ) {
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.applicationSqlBindingService = applicationSqlBindingService;
        this.applicationFunctionStore = applicationFunctionStore;
        this.schemaSession = schemaSession;
        this.eventBus = eventBus;
    }

    public void scheduleRefreshAfterFunction(String objectPath, String functionName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(objectPath, functionName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(objectPath, functionName);
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

    @Override
    public int order() {
        return 60;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.FUNCTION_SUCCEEDED || event.variableName() == null) {
            return;
        }
        String key = event.path() + '\0' + event.variableName();
        boolean alreadyRefreshing = committedDuringRefresh.merge(key, false, (pending, commit) -> true);
        if (alreadyRefreshing) {
            return;
        }
        boolean refreshing = true;
        try {
            while (refreshing) {
                refreshCommitted(event.path(), event.variableName());
                refreshing = committedDuringRefresh.computeIfPresent(key, (ignored, again) -> again ? false : null) != null;
            }
        } finally {
            if (refreshing) {
                committedDuringRefresh.remove(key);
            }
        }
    }

    private void submit(String objectPath, String functionName) {
        eventBus.submit(ObjectChangeEvent.functionSucceeded(objectPath, functionName));
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
