package com.ispf.server.workflow;

import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDueTimerSchedulerGateTest {

    @Mock
    WorkflowInstanceRepository instanceRepository;
    @Mock
    WorkflowInstanceStore instanceStore;
    @Mock
    WorkflowService workflowService;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    WorkflowObjectAccess objects;
    @Mock
    WorkflowInstance workflowInstance;

    private WorkflowDueTimerScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WorkflowDueTimerScheduler(
                instanceRepository,
                instanceStore,
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
        verify(instanceRepository, never()).findByStatusOrderByStartedAtDesc(anyString());
    }

    @Test
    void fireDueTimersCallsServiceForWaitingInstancesWithDueDeadline() throws Exception {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setId("inst-1");
        when(instanceRepository.findByStatusOrderByStartedAtDesc(InstanceStatus.WAITING.name()))
                .thenReturn(List.of(entity));
        when(instanceStore.load("inst-1")).thenReturn(
                new WorkflowInstanceStore.StoredWorkflowInstance(workflowInstance, null, Map.of())
        );
        when(workflowInstance.hasDueTimers(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);

        scheduler.fireDueTimers();

        verify(workflowService).fireDueTimers(eq("inst-1"), eq("scheduler"));
    }

    @Test
    void fireDueTimersSkipsInstancesWithoutDueDeadline() throws Exception {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setId("inst-2");
        when(instanceRepository.findByStatusOrderByStartedAtDesc(InstanceStatus.WAITING.name()))
                .thenReturn(List.of(entity));
        when(instanceStore.load("inst-2")).thenReturn(
                new WorkflowInstanceStore.StoredWorkflowInstance(workflowInstance, null, Map.of())
        );
        when(workflowInstance.hasDueTimers(org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);

        scheduler.fireDueTimers();

        verify(workflowService, never()).fireDueTimers(anyString(), anyString());
    }
}
