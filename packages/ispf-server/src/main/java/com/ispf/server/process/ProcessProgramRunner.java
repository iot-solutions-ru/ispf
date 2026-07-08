package com.ispf.server.process;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.expression.ExpressionEvaluationService;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Cyclic scheduler for PROCESS_PROGRAM objects (BL-172).
 */
@Component
public class ProcessProgramRunner {

    private static final String LOCK_NAME = "process_program_runner";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final ProcessProgramObjectService processProgramObjectService;
    private final ExpressionEvaluationService expressionEvaluationService;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;

    public ProcessProgramRunner(
            ProcessProgramObjectService processProgramObjectService,
            ExpressionEvaluationService expressionEvaluationService,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.processProgramObjectService = processProgramObjectService;
        this.expressionEvaluationService = expressionEvaluationService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(fixedDelayString = "${ispf.process-program.tick-ms:1000}")
    public void tick() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            runDuePrograms();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }

    void runDuePrograms() {
        Instant now = Instant.now();
        for (ProcessProgramObjectService.ProcessProgramDefinition program : processProgramObjectService.listEnabled()) {
            if (program.lastCycleAt() != null
                    && program.lastCycleAt().plusMillis(program.cycleIntervalMs()).isAfter(now)) {
                continue;
            }
            runProgram(program, now);
        }
    }

    private void runProgram(ProcessProgramObjectService.ProcessProgramDefinition program, Instant now) {
        String expression = program.controlExpression();
        if (expression == null || expression.isBlank()) {
            processProgramObjectService.recordCycle(program.path(), now, null);
            return;
        }
        try {
            ExpressionEvaluationService.EvaluateResult result = expressionEvaluationService.evaluate(
                    program.path(),
                    expression,
                    null
            );
            if (!result.valid()) {
                processProgramObjectService.recordCycle(program.path(), now, result.error());
                return;
            }
            processProgramObjectService.recordCycle(program.path(), now, null);
        } catch (Exception ex) {
            processProgramObjectService.recordCycle(program.path(), now, ex.getMessage());
        }
    }
}
