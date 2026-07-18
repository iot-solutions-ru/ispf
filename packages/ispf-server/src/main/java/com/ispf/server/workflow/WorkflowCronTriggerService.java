package com.ispf.server.workflow;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Cron-like poller for WORKFLOW.cronExpression (simple minute cadence; ADR-0049 Wave 3).
 * Supports expressions of the form {@code every:Nm} (every N minutes) for v1.
 */
@Service
public class WorkflowCronTriggerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCronTriggerService.class);

    private final ObjectManager objectManager;
    private final WorkflowService workflowService;

    public WorkflowCronTriggerService(ObjectManager objectManager, WorkflowService workflowService) {
        this.objectManager = objectManager;
        this.workflowService = workflowService;
    }

    @Scheduled(fixedDelayString = "${ispf.workflow.cron-poll-ms:60000}")
    public void poll() {
        try {
            for (PlatformObject child : objectManager.tree().childrenOf("root.platform.workflows")) {
                if (child.type() != ObjectType.WORKFLOW) {
                    continue;
                }
                String status = read(child, "status").orElse("DRAFT");
                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    continue;
                }
                String cron = read(child, "cronExpression").orElse("");
                if (!"every:1m".equalsIgnoreCase(cron.trim())) {
                    continue;
                }
                try {
                    workflowService.runWorkflow(
                            child.path(),
                            null,
                            AutomationMetricsRecorder.WorkflowStartTrigger.EVENT,
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
