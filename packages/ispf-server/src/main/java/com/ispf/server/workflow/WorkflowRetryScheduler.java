package com.ispf.server.workflow;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.persistence.entity.WorkflowRetryScheduleEntity;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leader-locked poller that executes due workflow retries (ADR-0049 Wave 3).
 */
@Component
public class WorkflowRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRetryScheduler.class);
    private static final String LOCK_NAME = "workflow_retry_scheduler";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final WorkflowRetryService retryService;
    private final WorkflowService workflowService;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;

    public WorkflowRetryScheduler(
            WorkflowRetryService retryService,
            @Lazy WorkflowService workflowService,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.retryService = retryService;
        this.workflowService = workflowService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(fixedDelayString = "${ispf.workflow.retry-poll-ms:5000}")
    public void poll() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            runDueRetries();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }

    void runDueRetries() {
        List<WorkflowRetryScheduleEntity> due = retryService.listDue(Instant.now());
        for (WorkflowRetryScheduleEntity row : due) {
            if (!retryService.claim(row.getId())) {
                continue;
            }
            try {
                Map<String, String> input = new HashMap<>(retryService.readInput(row));
                input.put("_retryAttempt", String.valueOf(row.getAttempt()));
                input.put("_retryScheduleId", row.getId());
                workflowService.runWorkflow(
                        row.getWorkflowPath(),
                        null,
                        AutomationMetricsRecorder.WorkflowStartTrigger.EVENT,
                        input
                );
                retryService.markDone(row.getId());
            } catch (Exception e) {
                log.warn("Workflow retry {} for {} failed: {}", row.getId(), row.getWorkflowPath(), e.getMessage());
                retryService.markFailed(row.getId(), e.getMessage());
            }
        }
    }
}
