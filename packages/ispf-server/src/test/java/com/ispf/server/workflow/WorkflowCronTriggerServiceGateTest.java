package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.spi.LeaderLock;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.spi.WorkflowStartTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowCronTriggerServiceGateTest {

    private static final String FIRST = "root.platform.workflows.cron-first";
    private static final String SECOND = "root.platform.workflows.cron-second";

    @Mock
    WorkflowObjectAccess objects;
    @Mock
    WorkflowService workflowService;
    @Mock
    WorkflowDeadLetterService deadLetterService;
    @Mock
    LeaderLock leaderLock;
    @Mock
    ClusterProperties clusterProperties;

    private WorkflowCronTriggerService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowCronTriggerService(
                objects,
                workflowService,
                deadLetterService,
                leaderLock,
                clusterProperties
        );
    }

    @Test
    void pollSkipsOnReplicaWithoutSchedulerCapability() throws Exception {
        when(clusterProperties.isSchedulerActive()).thenReturn(false);

        service.poll();

        verify(leaderLock, never()).runIfLeader(any(), any(), any());
        verify(objects, never()).childrenOf(anyString());
        verify(workflowService, never()).runWorkflow(any(), any(), any(), any());
    }

    @Test
    void pollSkipsWhileAnotherReplicaHoldsTheLease() throws Exception {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objects.isInitialized()).thenReturn(true);
        when(leaderLock.runIfLeader(eq(WorkflowCronTriggerService.LOCK_NAME), any(), any())).thenReturn(false);

        service.poll();

        verify(objects, never()).childrenOf(anyString());
        verify(workflowService, never()).runWorkflow(any(), any(), any(), any());
    }

    @Test
    void pollKeepsTheLeaseAfterTheTick() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objects.isInitialized()).thenReturn(true);
        leaderRunsTheTick();
        when(objects.childrenOf("root.platform.workflows")).thenReturn(List.of());

        service.poll();

        verify(objects).childrenOf("root.platform.workflows");
        verify(leaderLock, never()).release(anyString());
    }

    @Test
    void tickStopsBeforeTheNextWorkflowOnceTheLeaseIsLost() throws Exception {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objects.isInitialized()).thenReturn(true);
        leaderRunsTheTick();
        when(leaderLock.isHeld(WorkflowCronTriggerService.LOCK_NAME)).thenReturn(true, false);
        PlatformObject first = dueWorkflow(FIRST);
        PlatformObject second = dueWorkflow(SECOND);
        when(objects.childrenOf("root.platform.workflows")).thenReturn(List.of(first, second));

        service.poll();

        verify(workflowService).runWorkflow(
                eq(FIRST),
                isNull(),
                eq(WorkflowStartTrigger.EVENT),
                eq(Map.of("cronExpression", "every:1m"))
        );
        verify(workflowService, never()).runWorkflow(eq(SECOND), any(), any(), any());
    }

    private void leaderRunsTheTick() {
        when(leaderLock.runIfLeader(eq(WorkflowCronTriggerService.LOCK_NAME), any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        });
    }

    private static PlatformObject dueWorkflow(String path) {
        PlatformObject node = mock(PlatformObject.class);
        Variable status = stringVariable("ACTIVE");
        Variable cron = stringVariable("every:1m");
        lenient().when(node.type()).thenReturn(ObjectType.WORKFLOW);
        lenient().when(node.path()).thenReturn(path);
        lenient().when(node.getVariable("status")).thenReturn(Optional.of(status));
        lenient().when(node.getVariable("cronExpression")).thenReturn(Optional.of(cron));
        return node;
    }

    private static Variable stringVariable(String value) {
        Variable variable = mock(Variable.class);
        lenient().when(variable.value()).thenReturn(Optional.of(DataRecord.single(
                DataSchema.builder("value").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        )));
        return variable;
    }
}
