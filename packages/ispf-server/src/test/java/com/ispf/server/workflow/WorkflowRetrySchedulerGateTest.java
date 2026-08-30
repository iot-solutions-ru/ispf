package com.ispf.server.workflow;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
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
    ObjectManager objectManager;

    private WorkflowRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WorkflowRetryScheduler(
                retryService,
                workflowService,
                leaderLockService,
                clusterProperties,
                objectManager
        );
    }

    @Test
    void pollSkipsWhenObjectTreeNotReady() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objectManager.isInitialized()).thenReturn(false);

        scheduler.poll();

        verify(leaderLockService, never()).tryAcquire(any(), any());
        verify(retryService, never()).listDue(any());
    }
}
