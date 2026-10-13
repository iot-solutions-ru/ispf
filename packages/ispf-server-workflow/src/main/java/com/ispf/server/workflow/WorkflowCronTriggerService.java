package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.spi.LeaderLock;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.spi.WorkflowStartTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Leader-locked poller for WORKFLOW.cronExpression. Due rules match schedule objects:
 * {@code every:Nm} and 5/6-field cron, measured from {@code lastRunAt}.
 */
@Service
public class WorkflowCronTriggerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCronTriggerService.class);
    static final String LOCK_NAME = "workflow_cron_trigger";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    private final WorkflowObjectAccess objects;
    private final WorkflowService workflowService;
    private final WorkflowDeadLetterService deadLetterService;
    private final LeaderLock leaderLock;
    private final ClusterProperties clusterProperties;

    public WorkflowCronTriggerService(
            WorkflowObjectAccess objects,
            WorkflowService workflowService,
            WorkflowDeadLetterService deadLetterService,
            LeaderLock leaderLock,
            ClusterProperties clusterProperties
    ) {
        this.objects = objects;
        this.workflowService = workflowService;
        this.deadLetterService = deadLetterService;
        this.leaderLock = leaderLock;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(fixedDelayString = "${ispf.workflow.cron-poll-ms:60000}")
    public void poll() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!objects.isInitialized()) {
            return;
        }
        leaderLock.runIfLeader(LOCK_NAME, LOCK_TTL, this::runDueCrons);
    }

    void runDueCrons() {
        try {
            for (PlatformObject child : objects.childrenOf("root.platform.workflows")) {
                if (!leaderLock.isHeld(LOCK_NAME)) {
                    return;
                }
                if (child.type() != ObjectType.WORKFLOW) {
                    continue;
                }
                String status = read(child, "status").orElse("DRAFT");
                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    continue;
                }
                String cron = read(child, "cronExpression").orElse("");
                if (cron.isBlank()) {
                    continue;
                }
                try {
                    Instant lastRunAt = read(child, "lastRunAt").map(Instant::parse).orElse(null);
                    if (!WorkflowCronDue.isDue(Instant.now(), lastRunAt, cron)) {
                        continue;
                    }
                    workflowService.runWorkflow(
                            child.path(),
                            null,
                            WorkflowStartTrigger.EVENT,
                            Map.of("cronExpression", cron)
                    );
                } catch (Exception e) {
                    recordCronStartFailure(child.path(), cron, e);
                }
            }
        } catch (Exception e) {
            log.debug("Workflow cron poll skipped: {}", e.getMessage());
        }
    }

    private void recordCronStartFailure(String path, String cron, Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        deadLetterService.recordCommitted(
                "cron-start",
                path,
                1,
                message,
                "{\"cronExpression\":\"" + escapeJson(cron) + "\"}"
        );
        String state = "{\"status\":\"FAILED\",\"instanceId\":\"cron-start\",\"errorMessage\":\""
                + escapeJson(message) + "\"}";
        objects.setVariableValue(
                path,
                "instanceState",
                DataRecord.single(WorkflowTaskExecutor.STRING_VALUE, Map.of("value", state))
        );
        objects.persistNodeTree(path);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Optional<String> read(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(v -> !v.isBlank());
    }
}
