package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.spi.WorkflowMessageBus;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Projects a workflow instance onto its WORKFLOW object ({@code instanceState} / {@code lastRunAt})
 * and announces lifecycle transitions on the event bus. Called after every engine step.
 */
@Component
public class WorkflowInstanceStatePublisher {

    private final WorkflowObjectAccess objects;
    private final WorkflowMessageBus natsEventBridge;
    private final ObjectMapper objectMapper;

    public WorkflowInstanceStatePublisher(
            WorkflowObjectAccess objects,
            WorkflowMessageBus natsEventBridge,
            ObjectMapper objectMapper
    ) {
        this.objects = objects;
        this.natsEventBridge = natsEventBridge;
        this.objectMapper = objectMapper;
    }

    /** Persists the snapshot and publishes the transition event — the post-step pair every caller needs. */
    public void publish(String workflowPath, WorkflowInstance instance) {
        persistSnapshot(workflowPath, instance);
        publishEvent(workflowPath, instance);
    }

    void publishEvent(String path, WorkflowInstance instance) {
        if (instance.status() == InstanceStatus.COMPLETED) {
            natsEventBridge.publishWorkflowEvent(path, "completed", Map.of(
                    "instanceId", instance.instanceId(),
                    "status", instance.status().name()
            ));
        } else if (instance.status() == InstanceStatus.WAITING) {
            natsEventBridge.publishWorkflowEvent(path, "waiting", Map.of(
                    "instanceId", instance.instanceId(),
                    "taskId", instance.pendingUserTaskId().orElse(""),
                    "signal", instance.pendingSignalName().orElse(""),
                    "message", instance.pendingMessageName().orElse("")
            ));
        }
    }

    void persistSnapshot(String path, WorkflowInstance instance) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("instanceId", instance.instanceId());
            state.put("status", instance.status().name());
            state.put("currentNodeId", instance.currentNodeId());
            state.put("startedAt", instance.startedAt().toString());
            state.put("completedAt", instance.completedAt() != null ? instance.completedAt().toString() : null);
            state.put("history", instance.history());
            state.put("errorMessage", instance.errorMessage());
            state.put("assignee", instance.assignee().orElse(null));
            state.put("pendingUserTaskId", instance.pendingUserTaskId().orElse(null));
            state.put("pendingSignalName", instance.pendingSignalName().orElse(null));
            // ADR-0049: tool output projection and AI outputVariable read this map.
            state.put("variables", instance.variables() == null ? Map.of() : Map.copyOf(instance.variables()));

            String json = objectMapper.writeValueAsString(state);
            objects.setVariableValue(
                    path,
                    "instanceState",
                    DataRecord.single(WorkflowTaskExecutor.STRING_VALUE, Map.of("value", json))
            );
            objects.setVariableValue(
                    path,
                    "lastRunAt",
                    DataRecord.single(WorkflowTaskExecutor.STRING_VALUE, Map.of("value", Instant.now().toString()))
            );
            objects.persistNodeTree(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist workflow instance state", e);
        }
    }
}
