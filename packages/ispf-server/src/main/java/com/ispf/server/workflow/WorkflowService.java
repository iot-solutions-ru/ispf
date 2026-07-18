package com.ispf.server.workflow;

import com.ispf.server.cluster.NatsEventBridge;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.MessageTaskDefinition;
import com.ispf.plugin.workflow.ServiceTaskDefinition;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowActionType;
import com.ispf.plugin.workflow.WorkflowConditionEvaluator;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.function.FunctionInvocationScope;
import com.ispf.server.function.FunctionService;
import com.ispf.server.binding.BindingRefreshAfterCommit;
import com.ispf.server.event.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;
    private final WorkflowEngine workflowEngine;
    private final NatsEventBridge natsEventBridge;
    private final ObjectMapper objectMapper;
    private final WorkflowInstanceStore instanceStore;
    private final WorkflowConditionFactory conditionFactory;
    private final FunctionService functionService;
    private final EventService eventService;
    private final WorkQueueService workQueueService;
    private final WorkflowInstanceRepository instanceRepository;
    private final BindingRefreshAfterCommit bindingRefreshAfterCommit;
    private final WorkflowEventTriggerIndex eventTriggerIndex;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final WorkflowTriggerIndexRefresh triggerIndexRefresh;
    private final ObjectProvider<WorkflowService> self;
    private final WorkflowAiActionService workflowAiActionService;
    private final WorkflowDeadLetterService deadLetterService;
    private final WorkflowWebhookIndex webhookIndex;

    public WorkflowService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService,
            WorkflowEngine workflowEngine,
            NatsEventBridge natsEventBridge,
            ObjectMapper objectMapper,
            WorkflowInstanceStore instanceStore,
            WorkflowConditionFactory conditionFactory,
            FunctionService functionService,
            EventService eventService,
            @Lazy WorkQueueService workQueueService,
            WorkflowInstanceRepository instanceRepository,
            BindingRefreshAfterCommit bindingRefreshAfterCommit,
            WorkflowEventTriggerIndex eventTriggerIndex,
            AutomationMetricsRecorder automationMetricsRecorder,
            WorkflowTriggerIndexRefresh triggerIndexRefresh,
            ObjectProvider<WorkflowService> self,
            WorkflowAiActionService workflowAiActionService,
            WorkflowDeadLetterService deadLetterService,
            WorkflowWebhookIndex webhookIndex
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
        this.workflowEngine = workflowEngine;
        this.natsEventBridge = natsEventBridge;
        this.objectMapper = objectMapper;
        this.instanceStore = instanceStore;
        this.conditionFactory = conditionFactory;
        this.functionService = functionService;
        this.eventService = eventService;
        this.workQueueService = workQueueService;
        this.instanceRepository = instanceRepository;
        this.bindingRefreshAfterCommit = bindingRefreshAfterCommit;
        this.eventTriggerIndex = eventTriggerIndex;
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.triggerIndexRefresh = triggerIndexRefresh;
        this.self = self;
        this.workflowAiActionService = workflowAiActionService;
        this.deadLetterService = deadLetterService;
        this.webhookIndex = webhookIndex;
    }

    @Transactional
    public void ensureWorkflowStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.WORKFLOW) {
            throw new IllegalArgumentException("Not a workflow object: " + path);
        }
        structureService.ensureWorkflowStructure(path);
    }

    public WorkflowView getWorkflow(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.WORKFLOW) {
            throw new IllegalArgumentException("Not a workflow object: " + path);
        }
        return new WorkflowView(
                path,
                readString(node, "title").orElse(node.displayName()),
                readLifecycleStatus(node),
                readString(node, "bpmnXml").orElse(""),
                readString(node, "triggerJson").orElse("{}"),
                readString(node, "operatorAppId").orElse(null),
                readString(node, "instanceState").orElse("{}"),
                readString(node, "lastRunAt").orElse(null),
                readString(node, "inputSchemaJson").orElse("{}"),
                readString(node, "outputSchemaJson").orElse("{}"),
                readString(node, "toolDescription").orElse(""),
                readString(node, "sideEffectClass").orElse("WRITE"),
                readString(node, "webhookSlug").orElse(""),
                readString(node, "cronExpression").orElse("")
        );
    }

    public String resolveOperatorAppIdForPath(String workflowPath) {
        if (workflowPath == null || workflowPath.isBlank()) {
            return null;
        }
        try {
            PlatformObject node = objectManager.require(workflowPath);
            return readString(node, "operatorAppId").orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional
    public WorkflowView saveBpmn(String path, String bpmnXml) throws WorkflowException {
        workflowEngine.parse(bpmnXml);
        objectManager.setVariableValue(
                path,
                "bpmnXml",
                DataRecord.single(STRING_VALUE, Map.of("value", bpmnXml))
        );
        return getWorkflow(path);
    }

    @Transactional
    public WorkflowView updateStatus(String path, WorkflowLifecycleStatus status) {
        objectManager.setVariableValue(
                path,
                "status",
                DataRecord.single(STRING_VALUE, Map.of("value", status.name()))
        );
        triggerIndexRefresh.scheduleFullRebuild();
        webhookIndex.indexPath(path);
        return getWorkflow(path);
    }

    @Transactional
    public WorkflowView updateOperatorAppId(String path, String operatorAppId) {
        String normalized = operatorAppId != null ? operatorAppId.trim() : "";
        objectManager.setVariableValue(
                path,
                "operatorAppId",
                DataRecord.single(STRING_VALUE, Map.of("value", normalized))
        );
        return getWorkflow(path);
    }

    /**
     * Apply ADR-0049 tool-contract fields from an application bundle (null/blank = leave unchanged).
     */
    @Transactional
    public void applyExcellenceContract(
            String path,
            String title,
            String inputSchemaJson,
            String outputSchemaJson,
            String toolDescription,
            String sideEffectClass,
            String webhookSlug
    ) {
        ensureWorkflowStructure(path);
        setStringVarIfPresent(path, "title", title);
        setStringVarIfPresent(path, "inputSchemaJson", inputSchemaJson);
        setStringVarIfPresent(path, "outputSchemaJson", outputSchemaJson);
        setStringVarIfPresent(path, "toolDescription", toolDescription);
        setStringVarIfPresent(path, "sideEffectClass", sideEffectClass);
        setStringVarIfPresent(path, "webhookSlug", webhookSlug);
        webhookIndex.indexPath(path);
    }

    private void setStringVarIfPresent(String path, String variableName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        objectManager.setVariableValue(
                path,
                variableName,
                DataRecord.single(STRING_VALUE, Map.of("value", value))
        );
    }

    @Transactional
    public WorkflowView runWorkflow(String path) throws WorkflowException {
        return runWorkflow(path, null, AutomationMetricsRecorder.WorkflowStartTrigger.MANUAL);
    }

    @Transactional
    public WorkflowView runWorkflow(String path, String triggerObjectPath) throws WorkflowException {
        return runWorkflow(path, triggerObjectPath, AutomationMetricsRecorder.WorkflowStartTrigger.MANUAL);
    }

    @Transactional
    public WorkflowView runWorkflow(
            String path,
            String triggerObjectPath,
            AutomationMetricsRecorder.WorkflowStartTrigger trigger
    ) throws WorkflowException {
        return runWorkflow(path, triggerObjectPath, trigger, Map.of());
    }

    @Transactional
    public WorkflowView runWorkflow(
            String path,
            String triggerObjectPath,
            AutomationMetricsRecorder.WorkflowStartTrigger trigger,
            Map<String, String> input
    ) throws WorkflowException {
        automationMetricsRecorder.recordWorkflowStart(trigger);
        structureService.ensureWorkflowStructure(path);
        PlatformObject node = objectManager.require(path);
        String bpmnXml = readString(node, "bpmnXml").orElseThrow(() ->
                new WorkflowException("Workflow BPMN is empty: " + path));
        try {
            WorkflowToolContract.validateInput(
                    objectMapper,
                    readString(node, "inputSchemaJson").orElse("{}"),
                    input
            );
        } catch (WorkflowToolContract.WorkflowToolContractException e) {
            throw new WorkflowException(e.getMessage(), e);
        }
        BpmnProcess process = workflowEngine.parse(bpmnXml);
        WorkflowInstance instance = workflowEngine.start(path, process);
        if (triggerObjectPath != null) {
            instance.setVariable("triggerObjectPath", triggerObjectPath);
        }
        if (input != null) {
            input.forEach(instance::setVariable);
        }

        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(triggerObjectPath);
        while (instance.status() == InstanceStatus.RUNNING) {
            workflowEngine.step(instance, process, this::executeTask, this::executeMessageTask, evaluator);
        }

        UserTaskDefinition pendingTask = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, triggerObjectPath, pendingTask);
        persistInstanceSnapshot(path, instance);
        publishInstanceEvent(path, instance);
        if (instance.status() == InstanceStatus.FAILED) {
            handleFailure(path, instance, input);
        }
        return getWorkflow(path);
    }

    @Transactional
    public Map<String, Object> invokeWorkflowTool(String path, Map<String, String> input) throws WorkflowException {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.WORKFLOW) {
            throw new WorkflowException("Not a workflow: " + path);
        }
        structureService.ensureWorkflowStructure(path);
        if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
            throw new WorkflowException("Workflow tool requires ACTIVE status: " + path);
        }
        WorkflowView view = runWorkflow(
                path,
                null,
                AutomationMetricsRecorder.WorkflowStartTrigger.MANUAL,
                input == null ? Map.of() : input
        );
        Map<String, String> variables = Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> state = objectMapper.readValue(view.instanceState(), Map.class);
            Object vars = state.get("variables");
            if (vars instanceof Map<?, ?> map) {
                Map<String, String> parsed = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null) {
                        parsed.put(k.toString(), v == null ? "" : v.toString());
                    }
                });
                variables = parsed;
            }
        } catch (Exception ignored) {
            // keep empty
        }
        Map<String, String> output = WorkflowToolContract.extractOutput(
                objectMapper,
                view.outputSchemaJson(),
                variables
        );
        Map<String, Object> result = new HashMap<>();
        result.put("status", "OK");
        result.put("workflow", view);
        result.put("output", output);
        result.put("sideEffectClass", view.sideEffectClass());
        return result;
    }

    /**
     * ACTIVE workflows with non-blank {@code toolDescription} are published as MCP tools (ADR-0049).
     */
    @Transactional(readOnly = true)
    public List<PublishedWorkflowTool> listPublishedWorkflowTools() {
        List<PublishedWorkflowTool> tools = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.WORKFLOW) {
                continue;
            }
            if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
                continue;
            }
            String description = readString(node, "toolDescription").orElse("");
            if (description.isBlank()) {
                continue;
            }
            String toolName = mcpToolName(node.path(), usedNames);
            tools.add(new PublishedWorkflowTool(
                    toolName,
                    node.path(),
                    description,
                    readString(node, "inputSchemaJson").orElse("{}")
            ));
        }
        return List.copyOf(tools);
    }

    @Transactional(readOnly = true)
    public Optional<PublishedWorkflowTool> findPublishedWorkflowTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return listPublishedWorkflowTools().stream()
                .filter(tool -> tool.toolName().equals(toolName))
                .findFirst();
    }

    private static String mcpToolName(String path, Set<String> usedNames) {
        String last = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        String base = "wf_" + last.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        String candidate = base;
        if (!usedNames.add(candidate)) {
            String suffix = "_" + Integer.toHexString(path.hashCode());
            candidate = base.length() + suffix.length() > 64
                    ? base.substring(0, Math.max(1, 64 - suffix.length())) + suffix
                    : base + suffix;
            usedNames.add(candidate);
        }
        return candidate;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(String path) {
        return instanceStore.listRuns(path).stream().map(entity -> {
            Map<String, Object> row = new HashMap<>();
            row.put("instanceId", entity.getId());
            row.put("status", entity.getStatus());
            row.put("currentNodeId", entity.getCurrentNodeId());
            row.put("startedAt", entity.getStartedAt() == null ? null : entity.getStartedAt().toString());
            row.put("completedAt", entity.getCompletedAt() == null ? null : entity.getCompletedAt().toString());
            row.put("assignee", entity.getAssignee());
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSteps(String instanceId) {
        return instanceStore.listSteps(instanceId).stream().map(step -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", step.getId());
            row.put("seq", step.getSeq());
            row.put("tokenId", step.getTokenId());
            row.put("nodeId", step.getNodeId());
            row.put("nodeType", step.getNodeType());
            row.put("status", step.getStatus());
            row.put("attempt", step.getAttempt());
            row.put("startedAt", step.getStartedAt() == null ? null : step.getStartedAt().toString());
            row.put("endedAt", step.getEndedAt() == null ? null : step.getEndedAt().toString());
            row.put("inputJson", step.getInputJson());
            row.put("outputJson", step.getOutputJson());
            row.put("errorJson", step.getErrorJson());
            return row;
        }).toList();
    }

    private void handleFailure(String path, WorkflowInstance instance, Map<String, String> input) {
        PlatformObject node = objectManager.require(path);
        int attempt = parseInt(instance.variables().getOrDefault("_retryAttempt", "0"), 0) + 1;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "variables", instance.variables(),
                    "input", input == null ? Map.of() : input,
                    "error", instance.errorMessage() == null ? "" : instance.errorMessage(),
                    "retryMaxAttempts", readString(node, "retryMaxAttempts").orElse("0"),
                    "attempt", String.valueOf(attempt)
            ));
        } catch (Exception e) {
            payload = "{}";
        }
        deadLetterService.record(
                instance.instanceId(),
                path,
                attempt,
                instance.errorMessage(),
                payload
        );
        String errorWorkflow = readString(node, "errorWorkflowPath").orElse("");
        if (!errorWorkflow.isBlank() && !errorWorkflow.equals(path)) {
            try {
                self.getObject().runWorkflow(
                        errorWorkflow,
                        path,
                        AutomationMetricsRecorder.WorkflowStartTrigger.EVENT,
                        Map.of(
                                "failedWorkflowPath", path,
                                "failedInstanceId", instance.instanceId(),
                                "errorMessage", instance.errorMessage() == null ? "" : instance.errorMessage()
                        )
                );
            } catch (Exception e) {
                log.warn("errorWorkflowPath {} failed: {}", errorWorkflow, e.getMessage());
            }
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Transactional
    public void claimInstance(String instanceId, String operatorId) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        stored.instance().claim(operatorId);
        String bpmnXml = readString(objectManager.require(stored.instance().workflowPath()), "bpmnXml")
                .orElseThrow(() -> new WorkflowException("BPMN missing"));
        BpmnProcess process = workflowEngine.parse(bpmnXml);
        UserTaskDefinition pendingTask = stored.instance().pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(stored.instance(), process, stored.triggerObjectPath(), pendingTask);
    }

    @Transactional
    public WorkflowView completeUserTask(String instanceId, String operatorId) throws WorkflowException {
        return completeUserTask(instanceId, null, operatorId);
    }

    @Transactional
    public WorkflowView completeUserTask(String instanceId, String taskNodeId, String operatorId) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }

        String workflowPath = instance.workflowPath();
        BpmnProcess process = workflowEngine.parse(
                readString(objectManager.require(workflowPath), "bpmnXml")
                        .orElseThrow(() -> new WorkflowException("BPMN missing"))
        );

        String userTaskId = taskNodeId != null && !taskNodeId.isBlank()
                ? taskNodeId
                : instance.pendingUserTaskId()
                        .orElseThrow(() -> new WorkflowException("No pending user task"));
        UserTaskDefinition userTask = process.userTasks().get(userTaskId);
        if (userTask != null) {
            executeUserTaskAction(userTask, stored.triggerObjectPath());
        }

        instance.claim(operatorId);
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.completeUserTask(instance, process, userTaskId, this::executeTask, this::executeMessageTask, evaluator);

        UserTaskDefinition nextPending = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, stored.triggerObjectPath(), nextPending);
        persistInstanceSnapshot(workflowPath, instance);
        publishInstanceEvent(workflowPath, instance);
        return getWorkflow(workflowPath);
    }

    @Transactional
    public Map<String, Object> deliverSignal(String instanceId, String signalName, String operatorId)
            throws WorkflowException {
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal name is required");
        }
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }
        if (!instance.pendingSignalNames().contains(signalName)) {
            throw new WorkflowException("Instance is not waiting for signal: " + signalName);
        }

        String workflowPath = instance.workflowPath();
        BpmnProcess process = workflowEngine.parse(
                readString(objectManager.require(workflowPath), "bpmnXml")
                        .orElseThrow(() -> new WorkflowException("BPMN missing"))
        );

        if (operatorId != null && !operatorId.isBlank()) {
            instance.claim(operatorId);
        }
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.deliverSignal(
                instance,
                process,
                signalName,
                this::executeTask,
                this::executeMessageTask,
                evaluator
        );

        UserTaskDefinition nextPending = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, stored.triggerObjectPath(), nextPending);
        persistInstanceSnapshot(workflowPath, instance);
        publishInstanceEvent(workflowPath, instance);

        return Map.of(
                "instanceId", instanceId,
                "signal", signalName,
                "status", instance.status().name()
        );
    }

    @Transactional
    public Map<String, Object> deliverMessage(String instanceId, String messageName, String operatorId)
            throws WorkflowException {
        if (messageName == null || messageName.isBlank()) {
            throw new WorkflowException("Message name is required");
        }
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }
        if (!instance.pendingMessageNames().contains(messageName)) {
            throw new WorkflowException("Instance is not waiting for message: " + messageName);
        }

        String workflowPath = instance.workflowPath();
        BpmnProcess process = workflowEngine.parse(
                readString(objectManager.require(workflowPath), "bpmnXml")
                        .orElseThrow(() -> new WorkflowException("BPMN missing"))
        );

        if (operatorId != null && !operatorId.isBlank()) {
            instance.claim(operatorId);
        }
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.deliverMessage(
                instance,
                process,
                messageName,
                this::executeTask,
                this::executeMessageTask,
                evaluator
        );

        UserTaskDefinition nextPending = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, stored.triggerObjectPath(), nextPending);
        persistInstanceSnapshot(workflowPath, instance);
        publishInstanceEvent(workflowPath, instance);

        return Map.of(
                "instanceId", instanceId,
                "message", messageName,
                "status", instance.status().name()
        );
    }

    @Transactional
    public Map<String, Object> fireDueTimers(String instanceId, String operatorId) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }
        if (!instance.hasDueTimers(System.currentTimeMillis())) {
            throw new WorkflowException("No due timers for instance: " + instanceId);
        }

        String workflowPath = instance.workflowPath();
        BpmnProcess process = workflowEngine.parse(
                readString(objectManager.require(workflowPath), "bpmnXml")
                        .orElseThrow(() -> new WorkflowException("BPMN missing"))
        );

        if (operatorId != null && !operatorId.isBlank()) {
            instance.claim(operatorId);
        }
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.fireDueTimers(
                instance,
                process,
                this::executeTask,
                this::executeMessageTask,
                evaluator
        );

        UserTaskDefinition nextPending = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, stored.triggerObjectPath(), nextPending);
        persistInstanceSnapshot(workflowPath, instance);
        publishInstanceEvent(workflowPath, instance);

        return Map.of(
                "instanceId", instanceId,
                "status", instance.status().name()
        );
    }

    @Transactional
    public Map<String, Object> deliverSignalByWorkflowPath(String workflowPath, String signalName, String operatorId)
            throws WorkflowException {
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal name is required");
        }
        List<WorkflowInstanceEntity> waiting = instanceRepository.findByWorkflowPathAndStatus(
                workflowPath,
                InstanceStatus.WAITING.name()
        );
        List<String> signaled = new ArrayList<>();
        for (WorkflowInstanceEntity entity : waiting) {
            WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(entity.getId());
            if (!stored.instance().pendingSignalNames().contains(signalName)) {
                continue;
            }
            deliverSignal(entity.getId(), signalName, operatorId);
            signaled.add(entity.getId());
        }
        return Map.of(
                "workflowPath", workflowPath,
                "signal", signalName,
                "signaledCount", signaled.size(),
                "instanceIds", signaled
        );
    }

    @Transactional
    public Map<String, Object> deliverMessageByWorkflowPath(String workflowPath, String messageName, String operatorId)
            throws WorkflowException {
        if (messageName == null || messageName.isBlank()) {
            throw new WorkflowException("Message name is required");
        }
        List<WorkflowInstanceEntity> waiting = instanceRepository.findByWorkflowPathAndStatus(
                workflowPath,
                InstanceStatus.WAITING.name()
        );
        List<String> delivered = new ArrayList<>();
        for (WorkflowInstanceEntity entity : waiting) {
            WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(entity.getId());
            if (!stored.instance().pendingMessageNames().contains(messageName)) {
                continue;
            }
            deliverMessage(entity.getId(), messageName, operatorId);
            delivered.add(entity.getId());
        }
        return Map.of(
                "workflowPath", workflowPath,
                "message", messageName,
                "deliveredCount", delivered.size(),
                "instanceIds", delivered
        );
    }

    @Transactional
    public void handleVariableTrigger(String objectPath, String variableName) {
        for (String workflowPath : eventTriggerIndex.findVariableWorkflows(objectPath, variableName)) {
            PlatformObject node = objectManager.require(workflowPath);
            if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
                continue;
            }
            if (!isTriggerConditionMet(node, objectPath, variableName)) {
                continue;
            }
            try {
                runWorkflow(
                        workflowPath,
                        objectPath,
                        AutomationMetricsRecorder.WorkflowStartTrigger.VARIABLE
                );
            } catch (WorkflowException e) {
                log.warn("Workflow trigger failed for {}: {}", workflowPath, e.getMessage());
            }
        }
    }

    @Transactional
    public void handleEventTrigger(String objectPath, String eventName) {
        for (String workflowPath : eventTriggerIndex.findEventWorkflows(objectPath, eventName)) {
            PlatformObject node = objectManager.require(workflowPath);
            if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
                continue;
            }
            try {
                runWorkflow(
                        workflowPath,
                        objectPath,
                        AutomationMetricsRecorder.WorkflowStartTrigger.EVENT
                );
            } catch (WorkflowException e) {
                log.warn("Workflow event trigger failed for {}: {}", workflowPath, e.getMessage());
            }
        }
    }

    private void executeUserTaskAction(UserTaskDefinition userTask, String triggerObjectPath) {
        Map<String, String> params = userTask.parameters();
        String functionName = params.get("function");
        String targetObject = params.getOrDefault("targetObject", triggerObjectPath);
        if (functionName == null || functionName.isBlank() || targetObject == null || targetObject.isBlank()) {
            return;
        }
        try {
            FunctionInvocationScope.runSystemTrusted(() ->
                    functionService.invoke(targetObject, functionName));
        } catch (Exception e) {
            log.warn("User task function {} on {} failed: {}", functionName, targetObject, e.getMessage());
        }
    }

    private void executeMessageTask(MessageTaskDefinition task, WorkflowInstance instance) {
        if ("bpmn-throw".equalsIgnoreCase(task.channel())) {
            String messageName = task.subject();
            instance.resumeMessageIfPresent(messageName);
            try {
                self.getObject().deliverMessageByWorkflowPath(instance.workflowPath(), messageName, null);
            } catch (WorkflowException e) {
                log.debug("BPMN message throw had no external waiters for {}: {}", messageName, e.getMessage());
            }
            return;
        }
        if ("nats".equalsIgnoreCase(task.channel())) {
            natsEventBridge.publish(task.subject(), task.message());
            return;
        }
        log.info("[workflow:{}] message {} -> {}", instance.workflowPath(), task.subject(), task.message());
    }

    private void executeTask(ServiceTaskDefinition task, WorkflowInstance instance) throws WorkflowException {
        Map<String, String> params = task.parameters();
        switch (task.action()) {
            case LOG -> log.info("[workflow:{}] {}", instance.workflowPath(), params.getOrDefault("message", task.name()));
            case SET_VARIABLE -> {
                String target = required(params, "targetObject");
                String variable = required(params, "variable");
                String value = params.getOrDefault("value", "");
                objectManager.setVariableValue(
                        target,
                        variable,
                        DataRecord.single(STRING_VALUE, Map.of("value", value))
                );
            }
            case PUBLISH_NATS -> natsEventBridge.publish(
                    params.getOrDefault("subject", "ispf.workflow.event"),
                    params.getOrDefault("message", task.name())
            );
            case INVOKE_FUNCTION -> invokeWorkflowFunction(params, instance);
            case FIRE_EVENT -> {
                String target = params.getOrDefault("objectPath", params.getOrDefault("targetObject", ""));
                String eventName = required(params, "eventName");
                DataRecord payload = resolveEventPayload(target, params.get("payloadVariable"));
                eventService.fire(target, eventName, payload);
            }
            case READ_VARIABLE -> {
                String target = params.getOrDefault("objectPath", params.getOrDefault("targetObject", ""));
                String variable = params.get("sourceVariable");
                if (variable == null || variable.isBlank()) {
                    variable = required(params, "variable");
                }
                String valueField = params.getOrDefault("valueField", "value");
                String contextKey = params.getOrDefault("contextKey", variable);
                String value = readObjectVariableField(target, variable, valueField);
                instance.setVariable(contextKey, value);
            }
            case START_WORKFLOW -> {
                String childPath = required(params, "workflowPath");
                self.getObject().runWorkflow(
                        childPath,
                        params.get("objectPath"),
                        AutomationMetricsRecorder.WorkflowStartTrigger.EVENT
                );
            }
            case LLM_COMPLETE -> {
                String template = params.getOrDefault("promptTemplate", params.getOrDefault("message", ""));
                String prompt = WorkflowAiActionService.interpolate(template, instance.variables());
                int timeoutMs = parseInt(params.getOrDefault("timeoutMs", "30000"), 30_000);
                String content = workflowAiActionService.llmComplete(
                        prompt,
                        params.getOrDefault("modelRef", "platform-default"),
                        timeoutMs
                );
                String outputVariable = params.getOrDefault("outputVariable", "llmOutput");
                instance.setVariable(outputVariable, content);
            }
            case INVOKE_AGENT -> {
                String goalTemplate = params.getOrDefault("goalTemplate", params.getOrDefault("promptTemplate", ""));
                String goal = WorkflowAiActionService.interpolate(goalTemplate, instance.variables());
                String brief = workflowAiActionService.invokeAgent(
                        goal,
                        params.getOrDefault("agentMode", "ask"),
                        params.getOrDefault("toolAllowlist", ""),
                        parseInt(params.getOrDefault("maxSteps", "8"), 8)
                );
                String outputVariable = params.getOrDefault("outputVariable", "agentBrief");
                instance.setVariable(outputVariable, brief);
            }
        }
    }

    private DataRecord resolveEventPayload(String objectPath, String payloadVariable) {
        if (payloadVariable == null || payloadVariable.isBlank()) {
            return null;
        }
        PlatformObject node = objectManager.require(objectPath);
        return node.getVariable(payloadVariable)
                .flatMap(Variable::value)
                .orElse(null);
    }

    private String readObjectVariableField(String objectPath, String variableName, String valueField) {
        PlatformObject node = objectManager.require(objectPath);
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> {
                    Object value = record.firstRow().get(valueField);
                    return value != null ? String.valueOf(value) : "";
                })
                .orElse("");
    }

    private void invokeWorkflowFunction(Map<String, String> params, WorkflowInstance instance) throws WorkflowException {
        String objectPath = required(params, "objectPath");
        String functionName = required(params, "functionName");
        String inputMap = params.getOrDefault("inputMap", "");
        DataRecord input = buildWorkflowFunctionInput(inputMap, instance);
        DataRecord output = FunctionInvocationScope.callSystemTrusted(() ->
                functionService.invoke(objectPath, functionName, input));
        applyWorkflowFunctionOutput(params.get("outputMap"), output, instance);
        bindingRefreshAfterCommit.refreshNow(objectPath, functionName);
        if (output != null && output.rowCount() > 0) {
            Object errorCode = output.firstRow().get("error_code");
            if (errorCode != null && !"OK".equals(String.valueOf(errorCode))) {
                throw new WorkflowException("Function " + functionName + " failed: " + errorCode);
            }
        }
    }

    private DataRecord buildWorkflowFunctionInput(String inputMap, WorkflowInstance instance) {
        if (inputMap == null || inputMap.isBlank()) {
            return null;
        }
        Map<String, Object> row = new HashMap<>();
        DataSchema.Builder schemaBuilder = DataSchema.builder("workflowFunctionInput");
        for (String part : inputMap.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String valueExpr = kv[1].trim();
            String value = valueExpr.startsWith("${") && valueExpr.endsWith("}")
                    ? instance.variables().getOrDefault(valueExpr.substring(2, valueExpr.length() - 1), "")
                    : valueExpr;
            row.put(key, value);
            schemaBuilder.field(key, FieldType.STRING);
        }
        return row.isEmpty() ? null : DataRecord.single(schemaBuilder.build(), row);
    }

    private void applyWorkflowFunctionOutput(String outputMap, DataRecord output, WorkflowInstance instance) {
        if (outputMap == null || outputMap.isBlank() || output == null || output.rowCount() == 0) {
            return;
        }
        Map<String, Object> resultRow = output.firstRow();
        for (String part : outputMap.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String workflowVar = kv[0].trim();
            String resultField = kv[1].trim();
            instance.setVariable(workflowVar, String.valueOf(resultRow.get(resultField)));
        }
    }

    private void publishInstanceEvent(String path, WorkflowInstance instance) {
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

    private void persistInstanceSnapshot(String path, WorkflowInstance instance) {
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
            objectManager.setVariableValue(path, "instanceState", DataRecord.single(STRING_VALUE, Map.of("value", json)));
            objectManager.setVariableValue(
                    path,
                    "lastRunAt",
                    DataRecord.single(STRING_VALUE, Map.of("value", Instant.now().toString()))
            );
            objectManager.persistNodeTree(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist workflow instance state", e);
        }
    }

    private boolean isTriggerConditionMet(PlatformObject workflow, String objectPath, String variableName) {
        try {
            String triggerJson = readString(workflow, "triggerJson").orElse("{}");
            Optional<WorkflowEventTriggerIndex.TriggerBinding> binding =
                    WorkflowEventTriggerIndex.parseTrigger(workflow.path(), triggerJson, objectMapper);
            if (binding.isEmpty() || binding.get().triggerType() != WorkflowEventTriggerIndex.TriggerType.VARIABLE) {
                return false;
            }
            JsonNode trigger = objectMapper.readTree(triggerJson);
            String valueField = trigger.path("valueField").asText("value");
            JsonNode expected = trigger.get("expectedValue");
            if (expected == null || expected.isNull()) {
                return true;
            }

            PlatformObject source = objectManager.require(objectPath);
            Optional<Variable> variable = source.getVariable(variableName);
            if (variable.isEmpty() || variable.get().value().isEmpty()) {
                return false;
            }
            Object actual = variable.get().value().get().firstRow().get(valueField);
            if (expected.isBoolean()) {
                return Boolean.TRUE.equals(actual) || "true".equals(String.valueOf(actual));
            }
            return expected.asText().equals(String.valueOf(actual));
        } catch (Exception e) {
            log.debug("Trigger condition check failed for {}: {}", workflow.path(), e.getMessage());
            return false;
        }
    }

    private static String required(Map<String, String> params, String key) throws WorkflowException {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new WorkflowException("Missing service task parameter: " + key);
        }
        return value;
    }

    private static WorkflowLifecycleStatus readLifecycleStatus(PlatformObject node) {
        return readString(node, "status")
                .map(WorkflowLifecycleStatus::valueOf)
                .orElse(WorkflowLifecycleStatus.DRAFT);
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    public record WorkflowView(
            String path,
            String title,
            WorkflowLifecycleStatus status,
            String bpmnXml,
            String triggerJson,
            String operatorAppId,
            String instanceState,
            String lastRunAt,
            String inputSchemaJson,
            String outputSchemaJson,
            String toolDescription,
            String sideEffectClass,
            String webhookSlug,
            String cronExpression
    ) {
    }

    public record PublishedWorkflowTool(
            String toolName,
            String path,
            String description,
            String inputSchemaJson
    ) {
    }
}
