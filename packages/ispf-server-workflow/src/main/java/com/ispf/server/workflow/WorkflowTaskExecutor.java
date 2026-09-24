package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.workflow.CallActivityDefinition;
import com.ispf.plugin.workflow.CallActivityExecutor;
import com.ispf.plugin.workflow.MessageTaskDefinition;
import com.ispf.plugin.workflow.ServiceTaskDefinition;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.function.FunctionInvocationScope;
import com.ispf.server.spi.WorkflowBindingRefresh;
import com.ispf.server.spi.WorkflowEventPublish;
import com.ispf.server.spi.WorkflowFunctionCalls;
import com.ispf.server.spi.WorkflowMessageBus;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.spi.WorkflowStartTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Executes the BPMN node types the engine delegates to the platform: service tasks (log / set
 * variable / NATS / function / event / read variable / start workflow / LLM / agent), message
 * tasks, user-task completion hooks and callActivity child starts. Stateless apart from its
 * collaborators; {@link WorkflowService} owns instance lifecycle and persistence.
 *
 * <p>Re-entrant calls back into {@link WorkflowService} (child workflow start, BPMN message
 * throw) go through the {@link ObjectProvider} so they run on the transactional proxy.
 */
@Component
public class WorkflowTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskExecutor.class);

    static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final WorkflowObjectAccess objects;
    private final WorkflowMessageBus natsEventBridge;
    private final WorkflowFunctionCalls functionService;
    private final WorkflowEventPublish eventService;
    private final WorkflowBindingRefresh bindingRefreshAfterCommit;
    private final WorkflowAiActionService workflowAiActionService;
    private final ObjectProvider<WorkflowService> workflows;

    public WorkflowTaskExecutor(
            WorkflowObjectAccess objects,
            WorkflowMessageBus natsEventBridge,
            WorkflowFunctionCalls functionService,
            WorkflowEventPublish eventService,
            WorkflowBindingRefresh bindingRefreshAfterCommit,
            WorkflowAiActionService workflowAiActionService,
            ObjectProvider<WorkflowService> workflows
    ) {
        this.objects = objects;
        this.natsEventBridge = natsEventBridge;
        this.functionService = functionService;
        this.eventService = eventService;
        this.bindingRefreshAfterCommit = bindingRefreshAfterCommit;
        this.workflowAiActionService = workflowAiActionService;
        this.workflows = workflows;
    }

    /** Runs the {@code function} configured on a user task (if any) against its target object; failures are logged. */
    public void executeUserTaskAction(UserTaskDefinition userTask, String triggerObjectPath) {
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

    public void executeMessageTask(MessageTaskDefinition task, WorkflowInstance instance) {
        if ("bpmn-throw".equalsIgnoreCase(task.channel())) {
            String messageName = task.subject();
            instance.resumeMessageIfPresent(messageName);
            try {
                workflows.getObject().deliverMessageByWorkflowPath(instance.workflowPath(), messageName, null);
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

    /** Starts the child workflow of a BPMN callActivity synchronously and reports its outcome to the engine. */
    public CallActivityExecutor.Result executeCallActivity(
            CallActivityDefinition call,
            WorkflowInstance parent
    ) throws WorkflowException {
        Map<String, String> input = new HashMap<>();
        input.put("__callParentInstanceId", parent.instanceId());
        String inputMap = call.parameters().get("inputMap");
        if (inputMap != null && !inputMap.isBlank()) {
            for (String part : inputMap.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                input.put(kv[0].trim(), resolveMappedValue(kv[1].trim(), parent));
            }
        }
        String trigger = call.parameters().get("objectPath");
        if (trigger == null || trigger.isBlank()) {
            trigger = parent.variables().get("triggerObjectPath");
        }
        WorkflowInstance child = workflows.getObject().runWorkflowInstance(
                call.workflowPath(),
                trigger,
                WorkflowStartTrigger.EVENT,
                input
        );
        return new CallActivityExecutor.Result(
                child.status(),
                child.instanceId(),
                child.variables(),
                child.errorMessage()
        );
    }

    public void executeServiceTask(ServiceTaskDefinition task, WorkflowInstance instance) throws WorkflowException {
        Map<String, String> params = task.parameters();
        switch (task.action()) {
            case LOG -> log.info("[workflow:{}] {}", instance.workflowPath(), params.getOrDefault("message", task.name()));
            case SET_VARIABLE -> {
                String target = required(params, "targetObject");
                String variable = required(params, "variable");
                String value = params.getOrDefault("value", "");
                objects.setVariableValue(
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
                eventService.publishFired(target, eventName, payload);
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
                workflows.getObject().runWorkflow(
                        childPath,
                        params.get("objectPath"),
                        WorkflowStartTrigger.EVENT
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
        PlatformObject node = objects.require(objectPath);
        return node.getVariable(payloadVariable)
                .flatMap(Variable::value)
                .orElse(null);
    }

    private String readObjectVariableField(String objectPath, String variableName, String valueField) {
        PlatformObject node = objects.require(objectPath);
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

    private static DataRecord buildWorkflowFunctionInput(String inputMap, WorkflowInstance instance) {
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
            row.put(key, resolveMappedValue(kv[1].trim(), instance));
            schemaBuilder.field(key, FieldType.STRING);
        }
        return row.isEmpty() ? null : DataRecord.single(schemaBuilder.build(), row);
    }

    private static void applyWorkflowFunctionOutput(String outputMap, DataRecord output, WorkflowInstance instance) {
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

    /** {@code ${name}} reads an instance variable (empty when unset); anything else is a literal. */
    private static String resolveMappedValue(String valueExpr, WorkflowInstance instance) {
        return valueExpr.startsWith("${") && valueExpr.endsWith("}")
                ? instance.variables().getOrDefault(valueExpr.substring(2, valueExpr.length() - 1), "")
                : valueExpr;
    }

    private static String required(Map<String, String> params, String key) throws WorkflowException {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new WorkflowException("Missing service task parameter: " + key);
        }
        return value;
    }

    /** Lenient integer parse shared with retry-policy reads in {@link WorkflowService}. */
    static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
