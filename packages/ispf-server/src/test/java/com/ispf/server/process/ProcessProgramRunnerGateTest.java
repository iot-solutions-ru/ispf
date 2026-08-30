package com.ispf.server.process;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.expression.ExpressionEvaluationService;
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
class ProcessProgramRunnerGateTest {

    @Mock
    ProcessProgramObjectService processProgramObjectService;
    @Mock
    ExpressionEvaluationService expressionEvaluationService;
    @Mock
    ObjectManager objectManager;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ClusterProperties clusterProperties;

    private ProcessProgramRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ProcessProgramRunner(
                processProgramObjectService,
                expressionEvaluationService,
                objectManager,
                leaderLockService,
                clusterProperties
        );
    }

    @Test
    void tickSkipsWhenObjectTreeNotReady() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objectManager.isInitialized()).thenReturn(false);

        runner.tick();

        verify(leaderLockService, never()).tryAcquire(any(), any());
        verify(processProgramObjectService, never()).listEnabled();
    }
}
