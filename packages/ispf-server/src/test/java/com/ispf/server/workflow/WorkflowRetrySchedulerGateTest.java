package com.ispf.server.workflow;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ispf.server.persistence.entity.WorkflowRetryScheduleEntity;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRetrySchedulerGateTest {

    @Mock
    WorkflowRetryService retryService;
    @Mock
    WorkflowService workflowService;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    WorkflowObjectAccess objects;

    private WorkflowRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WorkflowRetryScheduler(
                retryService,
                workflowService,
                leaderLockService,
                clusterProperties,
                objects
        );
    }

    @Test
    void pollSkipsWhenObjectTreeNotReady() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objects.isInitialized()).thenReturn(false);

        scheduler.poll();

        verify(leaderLockService, never()).tryAcquire(any(), any());
        verify(retryService, never()).listDue(any());
    }

    @Test
    void unreadableInputFailsTheRetryInsteadOfRunningEmpty() throws Exception {
        WorkflowRetryScheduleEntity row = new WorkflowRetryScheduleEntity();
        row.setId("r-1");
        row.setWorkflowPath("root.platform.workflows.demo");
        row.setAttempt(1);
        when(retryService.listDue(any())).thenReturn(List.of(row));
        when(retryService.claim("r-1")).thenReturn(true);
        when(retryService.readInput(row)).thenThrow(
                new IllegalArgumentException("Workflow retry input JSON is not readable: bad")
        );

        scheduler.runDueRetries();

        verify(workflowService, never()).runWorkflow(any(), any(), any(), any());
        verify(retryService).markFailed(eq("r-1"), contains("not readable"));
    }
}
