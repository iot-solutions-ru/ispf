package com.ispf.server.binding;

import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeEventBus;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    @Mock
    ObjectChangeEventBus eventBus;

    private BindingRefreshAfterCommit refresh;

    @BeforeEach
    void setUp() {
        refresh = new BindingRefreshAfterCommit(
                sqlBindingObjectService,
                applicationSqlBindingService,
                applicationFunctionStore,
                schemaSession,
                eventBus
        );
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(schemaSession).runWithPlatformCatalog(any(Runnable.class));
        lenient().when(applicationFunctionStore.findLatest(HUB, FUNCTION)).thenReturn(Optional.of(
                new ApplicationFunctionHandler.DeployedFunction(
                        UUID.randomUUID(), "demo", HUB, FUNCTION, "1", "groovy", "", null, null)));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aCommittedFunctionHandsItsRefreshToTheBus() {
        TransactionSynchronizationManager.initSynchronization();

        refresh.scheduleRefreshAfterFunction(HUB, FUNCTION);
        verifyNoInteractions(eventBus);

        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(eventBus).submit(argThat(BindingRefreshAfterCommitTest::isFunctionCommit));
        verifyNoInteractions(sqlBindingObjectService, applicationSqlBindingService);
    }

    @Test
    void withoutATransactionTheRefreshGoesToTheBusRightAway() {
        refresh.scheduleRefreshAfterFunction(HUB, FUNCTION);

        verify(eventBus).submit(argThat(BindingRefreshAfterCommitTest::isFunctionCommit));
        verifyNoInteractions(sqlBindingObjectService, applicationSqlBindingService);
    }

    @Test
    void theBusRefreshesTheBindingsOfTheCommittedFunction() {
        refresh.handle(ObjectChangeEvent.functionSucceeded(HUB, FUNCTION));

        verify(sqlBindingObjectService).refreshAfterFunctionCommit(HUB, FUNCTION);
        verify(applicationSqlBindingService).refreshAfterFunctionCommit("demo", HUB, FUNCTION);
        verify(sqlBindingObjectService, never()).refreshAfterFunction(any(), any());
        verify(applicationSqlBindingService, never()).refreshAfterFunction(any(), any(), any());
    }

    @Test
    void otherChangesRefreshNothing() {
        refresh.handle(ObjectChangeEvent.eventFired(HUB, FUNCTION));
        refresh.handle(ObjectChangeEvent.variableUpdated(HUB, FUNCTION));

        verifyNoInteractions(sqlBindingObjectService, applicationSqlBindingService);
    }

    @Test
    void commitsDuringARefreshMakeItRunOnceMore() throws InterruptedException {
        AtomicInteger refreshes = new AtomicInteger();
        CountDownLatch firstRefreshStarted = new CountDownLatch(1);
        CountDownLatch finishFirstRefresh = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (refreshes.incrementAndGet() == 1) {
                firstRefreshStarted.countDown();
                assertThat(finishFirstRefresh.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(sqlBindingObjectService).refreshAfterFunctionCommit(HUB, FUNCTION);
        ObjectChangeEvent commit = ObjectChangeEvent.functionSucceeded(HUB, FUNCTION);
        Thread worker = new Thread(() -> refresh.handle(commit));
        worker.start();
        assertThat(firstRefreshStarted.await(5, TimeUnit.SECONDS)).isTrue();

        refresh.handle(commit);
        refresh.handle(commit);
        assertThat(refreshes).hasValue(1);
        finishFirstRefresh.countDown();
        worker.join(5_000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(refreshes).hasValue(2);
        refresh.handle(commit);
        assertThat(refreshes).hasValue(3);
    }

    @Test
    void aFailedRefreshDoesNotHoldBackTheNextOne() {
        doThrow(new IllegalStateException("catalog unavailable"))
                .doNothing()
                .when(sqlBindingObjectService).refreshAfterFunctionCommit(HUB, FUNCTION);
        ObjectChangeEvent commit = ObjectChangeEvent.functionSucceeded(HUB, FUNCTION);

        assertThatThrownBy(() -> refresh.handle(commit)).hasMessage("catalog unavailable");
        refresh.handle(commit);

        verify(sqlBindingObjectService, times(2)).refreshAfterFunctionCommit(HUB, FUNCTION);
    }

    @Test
    void workflowStepRefreshesInsideItsTransaction() {
        refresh.refreshNow(HUB, FUNCTION);

        verify(sqlBindingObjectService).refreshAfterFunction(HUB, FUNCTION);
        verify(applicationSqlBindingService).refreshAfterFunction("demo", HUB, FUNCTION);
        verify(sqlBindingObjectService, never()).refreshAfterFunctionCommit(any(), any());
        verify(applicationSqlBindingService, never()).refreshAfterFunctionCommit(any(), any(), any());
        verifyNoInteractions(eventBus);
    }

    private static boolean isFunctionCommit(ObjectChangeEvent event) {
        return event.type() == ObjectChangeType.FUNCTION_SUCCEEDED
                && HUB.equals(event.path())
                && FUNCTION.equals(event.variableName());
    }
}
