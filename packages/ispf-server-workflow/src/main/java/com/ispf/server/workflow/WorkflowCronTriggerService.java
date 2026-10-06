package com.ispf.server.workflow;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.spi.WorkflowStartTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Poller for WORKFLOW.cronExpression. Due rules match schedule objects:
 * {@code every:Nm} and 5/6-field cron, measured from {@code lastRunAt}.
 */
@Service
public class WorkflowCronTriggerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCronTriggerService.class);

    private final WorkflowObjectAccess objects;
    private final WorkflowService workflowService;

    public WorkflowCronTriggerService(WorkflowObjectAccess objects, WorkflowService workflowService) {
        this.objects = objects;
        this.workflowService = workflowService;
    }

    @Scheduled(fixedDelayString = "${ispf.workflow.cron-poll-ms:60000}")
    public void poll() {
        if (!objects.isInitialized()) {
            return;
        }
        try {
            for (PlatformObject child : objects.childrenOf("root.platform.workflows")) {
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
                    log.warn("Cron workflow {} failed: {}", child.path(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Workflow cron poll skipped: {}", e.getMessage());
        }
    }

    private static Optional<String> read(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(v -> !v.isBlank());
    }
}
