package com.ispf.server.workflow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.SequenceFlowDefinition;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowActionType;
import com.ispf.plugin.workflow.WorkflowConditionEvaluator;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import jakarta.annotation.PostConstruct;
import com.ispf.server.spi.WorkflowConditionCheck;
import com.ispf.server.spi.WorkflowMetrics;
import com.ispf.server.spi.WorkflowStartTrigger;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowDeadLetterEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final DataSchema STRING_VALUE = WorkflowTaskExecutor.STRING_VALUE;

    private final WorkflowObjectAccess objects;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final WorkflowInstanceStore instanceStore;
    private final WorkflowConditionFactory conditionFactory;
    private final WorkflowTaskExecutor taskExecutor;
    private final WorkflowInstanceStatePublisher statePublisher;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowEventTriggerIndex eventTriggerIndex;
    private final WorkflowMetrics automationMetricsRecorder;
    private final WorkflowTriggerIndexRefresh triggerIndexRefresh;
    private final ObjectProvider<WorkflowService> self;
    private final WorkflowDeadLetterService deadLetterService;
    private final WorkflowWebhookIndex webhookIndex;
    private final WorkflowRetryService retryService;
    private final WorkflowConditionCheck formalVerificationService;
    private final WorkflowInstanceControl instanceControl;

    public WorkflowService(
            WorkflowObjectAccess objects,
            WorkflowEngine workflowEngine,
            ObjectMapper objectMapper,
            WorkflowInstanceStore instanceStore,
            WorkflowConditionFactory conditionFactory,
            WorkflowTaskExecutor taskExecutor,
            WorkflowInstanceStatePublisher statePublisher,
            WorkflowInstanceRepository instanceRepository,
            WorkflowEventTriggerIndex eventTriggerIndex,
            WorkflowMetrics automationMetricsRecorder,
            WorkflowTriggerIndexRefresh triggerIndexRefresh,
            ObjectProvider<WorkflowService> self,
            WorkflowDeadLetterService deadLetterService,
            WorkflowWebhookIndex webhookIndex,
            WorkflowRetryService retryService,
            WorkflowConditionCheck formalVerificationService
    ) {
        this.objects = objects;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
        this.instanceStore = instanceStore;
        this.conditionFactory = conditionFactory;
        this.taskExecutor = taskExecutor;
        this.statePublisher = statePublisher;
        this.instanceRepository = instanceRepository;
        this.eventTriggerIndex = eventTriggerIndex;
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.triggerIndexRefresh = triggerIndexRefresh;
        this.self = self;
        this.deadLetterService = deadLetterService;
        this.webhookIndex = webhookIndex;
        this.retryService = retryService;
        this.formalVerificationService = formalVerificationService;
        this.instanceControl = new WorkflowInstanceControl(
                this,
                objects,
                workflowEngine,
                instanceStore,
                taskExecutor,
                conditionFactory,
                instanceRepository
        );
    }

    @PostConstruct
    void wireCallActivityExecutor() {
        workflowEngine.setCallActivityExecutor(taskExecutor::executeCallActivity);
    }

    @Transactional
    public void ensureWorkflowStructure(String path) {
        PlatformObject node = objects.require(path);
        if (node.type() != ObjectType.WORKFLOW) {
            throw new IllegalArgumentException("Not a workflow object: " + path);
        }
        objects.ensureWorkflowStructure(path);
    }

    public WorkflowView getWorkflow(String path) {
        PlatformObject node = objects.require(path);
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
            PlatformObject node = objects.require(workflowPath);
            return readString(node, "operatorAppId").orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional
    public WorkflowView saveBpmn(String path, String bpmnXml) throws WorkflowException {
        BpmnProcess process = workflowEngine.parse(bpmnXml);
        verifySequenceFlowConditions(process);
        objects.setVariableValue(
                path,
                "bpmnXml",
                DataRecord.single(STRING_VALUE, Map.of("value", bpmnXml))
        );
        return getWorkflow(path);
    }

    @Transactional
    public WorkflowView updateStatus(String path, WorkflowLifecycleStatus status) {
        if (status == WorkflowLifecycleStatus.ACTIVE) {
            PlatformObject node = objects.require(path);
            String bpmnXml = readString(node, "bpmnXml").orElse("");
            if (!bpmnXml.isBlank()) {
                try {
                    verifySequenceFlowConditions(workflowEngine.parse(bpmnXml));
                } catch (WorkflowException ex) {
                    throw new IllegalArgumentException("Cannot activate workflow with invalid BPMN: " + ex.getMessage(), ex);
                }
            }
        }
        objects.setVariableValue(
                path,
                "status",
                DataRecord.single(STRING_VALUE, Map.of("value", status.name()))
        );
        triggerIndexRefresh.scheduleFullRebuild();
        webhookIndex.indexPath(path);
        return getWorkflow(path);
    }

    private void verifySequenceFlowConditions(BpmnProcess process) {
        for (SequenceFlowDefinition flow : process.sequenceFlows()) {
            String condition = flow.conditionExpression();
            if (condition == null || condition.isBlank()) {
                continue;
            }
            try {
                formalVerificationService.requireSafeCondition(condition.trim());
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(
                        "Formal verification rejected sequence flow '"
                                + flow.id()
                                + "' condition: "
                                + ex.getMessage(),
                        ex
                );
            }
        }
    }

    @Transactional
    public WorkflowView updateOperatorAppId(String path, String operatorAppId) {
        String normalized = operatorAppId != null ? operatorAppId.trim() : "";
        objects.setVariableValue(
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
        objects.setVariableValue(
                path,
                variableName,
                DataRecord.single(STRING_VALUE, Map.of("value", value))
        );
    }

    @Transactional
    public WorkflowView runWorkflow(String path) throws WorkflowException {
        return runWorkflow(path, null, WorkflowStartTrigger.MANUAL);
    }

    @Transactional
    public WorkflowView runWorkflow(String path, String triggerObjectPath) throws WorkflowException {
        return runWorkflow(path, triggerObjectPath, WorkflowStartTrigger.MANUAL);
    }

    @Transactional
    public WorkflowView runWorkflow(
            String path,
            String triggerObjectPath,
            WorkflowStartTrigger trigger
    ) throws WorkflowException {
        return runWorkflow(path, triggerObjectPath, trigger, Map.of());
    }

    @Transactional
    public WorkflowView runWorkflow(
            String path,
            String triggerObjectPath,
            WorkflowStartTrigger trigger,
            Map<String, String> input
    ) throws WorkflowException {
        runWorkflowInstance(path, triggerObjectPath, trigger, input);
        return getWorkflow(path);
    }

    /**
     * Starts a workflow and returns the runtime instance (used by callActivity and tests).
     */
    @Transactional
    public WorkflowInstance runWorkflowInstance(
            String path,
            String triggerObjectPath,
            WorkflowStartTrigger trigger,
            Map<String, String> input
    ) throws WorkflowException {
        automationMetricsRecorder.recordWorkflowStart(trigger);
        objects.ensureWorkflowStructure(path);
        PlatformObject node = objects.require(path);
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
            workflowEngine.step(
                    instance,
                    process,
                    taskExecutor::executeServiceTask,
                    taskExecutor::executeMessageTask,
                    evaluator
            );
        }
        finishStep(triggerObjectPath, instance, process, input);
        return instance;
    }

    @Transactional
    public Map<String, Object> invokeWorkflowTool(String path, Map<String, String> input) throws WorkflowException {
        PlatformObject node = objects.require(path);
        if (node.type() != ObjectType.WORKFLOW) {
            throw new WorkflowException("Not a workflow: " + path);
        }
        objects.ensureWorkflowStructure(path);
        if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
            throw new WorkflowException("Workflow tool requires ACTIVE status: " + path);
        }
        WorkflowView view = runWorkflow(
                path,
                null,
                WorkflowStartTrigger.MANUAL,
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
        for (PlatformObject node : objects.all()) {
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDeadLetters(String path, boolean unresolvedOnly) {
        var rows = unresolvedOnly
                ? deadLetterService.listUnresolvedByPath(path)
                : deadLetterService.listByPath(path);
        return rows.stream().map(this::toDeadLetterRow).toList();
    }

    @Transactional
    public Map<String, Object> resolveDeadLetter(String id) {
        return toDeadLetterRow(deadLetterService.resolve(id));
    }

    private Map<String, Object> toDeadLetterRow(WorkflowDeadLetterEntity entity) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", entity.getId());
        row.put("instanceId", entity.getInstanceId());
        row.put("workflowPath", entity.getWorkflowPath());
        row.put("attemptCount", entity.getAttemptCount());
        row.put("lastError", entity.getLastError());
        row.put("payloadJson", entity.getPayloadJson());
        row.put("createdAt", entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
        if (entity.getResolvedAt() != null) {
            row.put("resolvedAt", entity.getResolvedAt().toString());
        }
        return row;
    }

    private void handleFailure(String path, WorkflowInstance instance, Map<String, String> input) {
        PlatformObject node = objects.require(path);
        int attempt = WorkflowTaskExecutor.parseInt(instance.variables().getOrDefault("_retryAttempt", "0"), 0) + 1;
        int maxAttempts = WorkflowTaskExecutor.parseInt(readString(node, "retryMaxAttempts").orElse("0"), 0);
        int backoffSeconds = Math.max(0, WorkflowTaskExecutor.parseInt(readString(node, "retryBackoffSeconds").orElse("30"), 30));
        if (maxAttempts > 0 && attempt < maxAttempts) {
            Map<String, String> retryInput = new HashMap<>(input == null ? Map.of() : input);
            retryInput.put("_retryAttempt", String.valueOf(attempt));
            Instant dueAt = Instant.now().plusSeconds(backoffSeconds);
            retryService.schedule(
                    path,
                    instance.instanceId(),
                    attempt,
                    dueAt,
                    retryInput,
                    instance.errorMessage()
            );
            log.info("Scheduled async retry for {} attempt {}/{} due {}", path, attempt + 1, maxAttempts, dueAt);
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "variables", instance.variables(),
                    "input", input == null ? Map.of() : input,
                    "error", instance.errorMessage() == null ? "" : instance.errorMessage(),
                    "retryMaxAttempts", String.valueOf(maxAttempts),
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
                        WorkflowStartTrigger.EVENT,
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

    @Transactional
    public void claimInstance(String instanceId, String operatorId) throws WorkflowException {
        instanceControl.claimInstance(instanceId, operatorId);
    }

    @Transactional
    public WorkflowView completeUserTask(String instanceId, String operatorId) throws WorkflowException {
        return completeUserTask(instanceId, null, operatorId);
    }

    @Transactional
    public WorkflowView completeUserTask(String instanceId, String taskNodeId, String operatorId) throws WorkflowException {
        return instanceControl.completeUserTask(instanceId, taskNodeId, operatorId);
    }

    @Transactional
    public Map<String, Object> deliverSignal(String instanceId, String signalName, String operatorId)
            throws WorkflowException {
        return instanceControl.deliverSignal(instanceId, signalName, operatorId);
    }

    @Transactional
    public Map<String, Object> deliverMessage(String instanceId, String messageName, String operatorId)
            throws WorkflowException {
        return instanceControl.deliverMessage(instanceId, messageName, operatorId);
    }

    @Transactional
    public Map<String, Object> fireDueTimers(String instanceId, String operatorId) throws WorkflowException {
        return instanceControl.fireDueTimers(instanceId, operatorId);
    }

    @Transactional
    public Map<String, Object> deliverSignalByWorkflowPath(String workflowPath, String signalName, String operatorId)
            throws WorkflowException {
        return instanceControl.deliverSignalByWorkflowPath(workflowPath, signalName, operatorId);
    }

    @Transactional
    public Map<String, Object> deliverMessageByWorkflowPath(String workflowPath, String messageName, String operatorId)
            throws WorkflowException {
        return instanceControl.deliverMessageByWorkflowPath(workflowPath, messageName, operatorId);
    }

    @Transactional
    public void handleVariableTrigger(String objectPath, String variableName) {
        for (String workflowPath : eventTriggerIndex.findVariableWorkflows(objectPath, variableName)) {
            PlatformObject node;
            try {
                node = objects.require(workflowPath);
            } catch (ObjectNotFoundException ex) {
                log.warn("Skipping variable trigger for missing workflow {}: {}", workflowPath, ex.getMessage());
                eventTriggerIndex.removeWorkflow(workflowPath);
                continue;
            }
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
                        WorkflowStartTrigger.VARIABLE
                );
            } catch (WorkflowException e) {
                log.warn("Workflow trigger failed for {}: {}", workflowPath, e.getMessage());
            }
        }
    }

    @Transactional
    public void handleEventTrigger(String objectPath, String eventName) {
        for (String workflowPath : eventTriggerIndex.findEventWorkflows(objectPath, eventName)) {
            PlatformObject node;
            try {
                node = objects.require(workflowPath);
            } catch (ObjectNotFoundException ex) {
                log.warn("Skipping event trigger for missing workflow {}: {}", workflowPath, ex.getMessage());
                eventTriggerIndex.removeWorkflow(workflowPath);
                continue;
            }
            if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
                continue;
            }
            try {
                runWorkflow(
                        workflowPath,
                        objectPath,
                        WorkflowStartTrigger.EVENT
                );
            } catch (WorkflowException e) {
                log.warn("Workflow event trigger failed for {}: {}", workflowPath, e.getMessage());
            }
        }
    }

    private void notifyCallActivityParents(WorkflowInstance child) {
        if (child.status() != InstanceStatus.COMPLETED && child.status() != InstanceStatus.FAILED) {
            return;
        }
        String parentId = child.variables().get("__callParentInstanceId");
        if (parentId == null || parentId.isBlank()) {
            return;
        }
        try {
            self.getObject().resumeParentCallActivity(
                    parentId,
                    child.instanceId(),
                    child.status() == InstanceStatus.FAILED ? child.errorMessage() : null,
                    child.variables()
            );
        } catch (WorkflowException e) {
            log.debug(
                    "callActivity parent {} not resumed for child {}: {}",
                    parentId,
                    child.instanceId(),
                    e.getMessage()
            );
        }
    }

    /**
     * Resumes a parent instance waiting on {@code childInstanceId} (BPMN callActivity).
     * No-op when the parent is not waiting on that child (sync child completion path).
     */
    @Transactional
    public void resumeParentCallActivity(
            String parentInstanceId,
            String childInstanceId,
            String childFailedMessage,
            Map<String, String> childVariables
    ) throws WorkflowException {
        instanceControl.resumeParentCallActivity(parentInstanceId, childInstanceId, childFailedMessage, childVariables);
    }

    /**
     * Post-step commit shared by every entry point that advances an instance: store the runtime
     * state, project it onto the WORKFLOW object, publish the transition, route failures to
     * retry / dead-letter, and wake a parent callActivity that may be waiting on this instance.
     */
    void finishStep(
            String triggerObjectPath,
            WorkflowInstance instance,
            BpmnProcess process,
            Map<String, String> failureInput
    ) {
        UserTaskDefinition nextPending = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, triggerObjectPath, nextPending);
        String workflowPath = instance.workflowPath();
        statePublisher.publish(workflowPath, instance);
        if (instance.status() == InstanceStatus.FAILED) {
            handleFailure(workflowPath, instance, failureInput);
        }
        notifyCallActivityParents(instance);
    }

    BpmnProcess parseProcess(String workflowPath) throws WorkflowException {
        return workflowEngine.parse(
                readString(objects.require(workflowPath), "bpmnXml")
                        .orElseThrow(() -> new WorkflowException("BPMN missing"))
        );
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

            PlatformObject source = objects.require(objectPath);
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

    private static WorkflowLifecycleStatus readLifecycleStatus(PlatformObject node) {
        return readString(node, "status")
                .map(WorkflowLifecycleStatus::valueOf)
                .orElse(WorkflowLifecycleStatus.DRAFT);
    }

    static Optional<String> readString(PlatformObject node, String variableName) {
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
