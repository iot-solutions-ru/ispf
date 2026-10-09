package com.ispf.server.workflow;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.spi.LeaderLock;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowCronTriggerServiceGateTest {

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

        verify(leaderLock, never()).tryAcquire(anyString(), any());
        verify(objects, never()).childrenOf(anyString());
        verify(workflowService, never()).runWorkflow(any(), any(), any(), any());
    }

    @Test
    void pollSkipsWhileAnotherReplicaHoldsTheLock() throws Exception {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objects.isInitialized()).thenReturn(true);
        when(leaderLock.tryAcquire(eq(WorkflowCronTriggerService.LOCK_NAME), any())).thenReturn(false);

        service.poll();

        verify(objects, never()).childrenOf(anyString());
        verify(workflowService, never()).runWorkflow(any(), any(), any(), any());
        verify(leaderLock, never()).release(anyString());
    }

    @Test
    void pollReleasesTheLockAfterTheTick() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objects.isInitialized()).thenReturn(true);
        when(leaderLock.tryAcquire(eq(WorkflowCronTriggerService.LOCK_NAME), any())).thenReturn(true);
        when(objects.childrenOf("root.platform.workflows")).thenReturn(List.of());

        service.poll();

        verify(leaderLock).release(WorkflowCronTriggerService.LOCK_NAME);
    }
}
