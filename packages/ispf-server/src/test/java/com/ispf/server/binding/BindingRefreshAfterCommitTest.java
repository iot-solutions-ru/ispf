package com.ispf.server.binding;

import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingRefreshAfterCommitTest {

    private static final String HUB = "root.platform.singleton-blueprints.kpi-hub";
    private static final String FUNCTION = "recalc";

    @Mock
    SqlBindingObjectService sqlBindingObjectService;
    @Mock
    ApplicationSqlBindingService applicationSqlBindingService;
    @Mock
    ApplicationFunctionStore applicationFunctionStore;
    @Mock
    ApplicationSchemaSession schemaSession;

    private BindingRefreshAfterCommit refresh;

    @BeforeEach
    void setUp() {
        refresh = new BindingRefreshAfterCommit(
                sqlBindingObjectService,
                applicationSqlBindingService,
                applicationFunctionStore,
                schemaSession
        );
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(schemaSession).runWithPlatformCatalog(any(Runnable.class));
        when(applicationFunctionStore.findLatest(HUB, FUNCTION)).thenReturn(Optional.of(new ApplicationFunctionHandler.DeployedFunction(
                UUID.randomUUID(), "demo", HUB, FUNCTION, "1", "groovy", "", null, null)));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void bindingsRefreshOnTheirOwnOnceTheFunctionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        refresh.scheduleRefreshAfterFunction(HUB, FUNCTION);
        verifyNoInteractions(sqlBindingObjectService, applicationSqlBindingService);

        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(sqlBindingObjectService).refreshAfterFunctionCommit(HUB, FUNCTION);
        verify(applicationSqlBindingService).refreshAfterFunctionCommit("demo", HUB, FUNCTION);
        verify(sqlBindingObjectService, never()).refreshAfterFunction(any(), any());
        verify(applicationSqlBindingService, never()).refreshAfterFunction(any(), any(), any());
    }

    @Test
    void withoutATransactionTheBindingsRefreshOnTheirOwnRightAway() {
        refresh.scheduleRefreshAfterFunction(HUB, FUNCTION);

        verify(sqlBindingObjectService).refreshAfterFunctionCommit(HUB, FUNCTION);
        verify(applicationSqlBindingService).refreshAfterFunctionCommit("demo", HUB, FUNCTION);
    }

    @Test
    void workflowStepRefreshesInsideItsTransaction() {
        refresh.refreshNow(HUB, FUNCTION);

        verify(sqlBindingObjectService).refreshAfterFunction(HUB, FUNCTION);
        verify(applicationSqlBindingService).refreshAfterFunction("demo", HUB, FUNCTION);
        verify(sqlBindingObjectService, never()).refreshAfterFunctionCommit(any(), any());
        verify(applicationSqlBindingService, never()).refreshAfterFunctionCommit(any(), any(), any());
    }
}
