package com.ispf.server.process;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.expression.ExpressionEvaluationService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    void missingTargetObjectIsSoftFailedWithoutAbortingTick() {
        String programPath = ProcessProgramPaths.PROCESS_PROGRAMS_ROOT + ".missing-target";
        String missingTarget = "root.platform.devices.missing-process-target";
        ProcessProgramObjectService.ProcessProgramDefinition program =
                new ProcessProgramObjectService.ProcessProgramDefinition(
                        programPath,
                        "missing-target",
                        1,
                        "true",
                        missingTarget,
                        "outputVar",
                        null,
                        true,
                        null,
                        null,
                        null
                );
        when(processProgramObjectService.listEnabled()).thenReturn(List.of(program));
        when(expressionEvaluationService.evaluate(eq(missingTarget), eq("true"), isNull()))
                .thenReturn(new ExpressionEvaluationService.EvaluateResult(
                        true,
                        "true",
                        true,
                        "Boolean",
                        null,
                        List.of(),
                        false,
                        null
                ));
        when(objectManager.require(missingTarget)).thenThrow(new ObjectNotFoundException(missingTarget));

        assertThatCode(() -> runner.runDuePrograms()).doesNotThrowAnyException();

        verify(processProgramObjectService).recordCycle(
                eq(programPath),
                any(Instant.class),
                eq("Object not found: " + missingTarget)
        );
    }
}
