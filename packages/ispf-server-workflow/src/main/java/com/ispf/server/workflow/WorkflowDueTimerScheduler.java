package com.ispf.server.workflow;

import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.ispf.server.spi.LeaderLock;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Leader-locked poller that fires due BPMN boundary and intermediate catch timers.
 * Manual {@code POST .../instances/{id}/timer} remains for tests and forced fire.
 */
@Component
public class WorkflowDueTimerScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDueTimerScheduler.class);
    private static final String LOCK_NAME = "workflow_due_timer_scheduler";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String OPERATOR_ID = "scheduler";

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowInstanceStore instanceStore;
    private final WorkflowService workflowService;
    private final LeaderLock leaderLockService;
    private final ClusterProperties clusterProperties;
    private final WorkflowObjectAccess objects;

    public WorkflowDueTimerScheduler(
            WorkflowInstanceRepository instanceRepository,
            WorkflowInstanceStore instanceStore,
            @Lazy WorkflowService workflowService,
            LeaderLock leaderLockService,
            ClusterProperties clusterProperties,
            WorkflowObjectAccess objects
    ) {
        this.instanceRepository = instanceRepository;
        this.instanceStore = instanceStore;
        this.workflowService = workflowService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
        this.objects = objects;
    }

    @Scheduled(fixedDelayString = "${ispf.workflow.timer-poll-ms:2000}")
    public void poll() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!objects.isInitialized()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            fireDueTimers();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }

    void fireDueTimers() {
        long nowEpochMs = System.currentTimeMillis();
        List<WorkflowInstanceEntity> waiting = instanceRepository.findByStatusOrderByStartedAtDesc(
                InstanceStatus.WAITING.name()
        );
        for (WorkflowInstanceEntity entity : waiting) {
            String instanceId = entity.getId();
            try {
                WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
                if (!stored.instance().hasDueTimers(nowEpochMs)) {
                    continue;
                }
                workflowService.fireDueTimers(instanceId, OPERATOR_ID);
            } catch (WorkflowException ex) {
                log.debug("Skip due timer for {}: {}", instanceId, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("Due timer fire failed for {}: {}", instanceId, ex.getMessage());
            }
        }
    }
}
