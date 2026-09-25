package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.workflow.CallActivityDefinition;
import com.ispf.plugin.workflow.CallActivityExecutor;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.MessageTaskDefinition;
import com.ispf.plugin.workflow.ServiceTaskDefinition;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowActionType;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.spi.WorkflowBindingRefresh;
import com.ispf.server.spi.WorkflowEventPublish;
import com.ispf.server.spi.WorkflowFunctionCalls;
import com.ispf.server.spi.WorkflowMessageBus;
import com.ispf.server.spi.WorkflowStartTrigger;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTaskExecutorTest {

    private static final String WORKFLOW = "root.platform.workflows.demo";
    private static final String TARGET = "root.devices.pump";

    @Mock
    private WorkflowObjectAccess objects;
    @Mock
    private WorkflowMessageBus natsEventBridge;
    @Mock
    private WorkflowFunctionCalls functionService;
    @Mock
    private WorkflowEventPublish eventService;
    @Mock
    private WorkflowBindingRefresh bindingRefreshAfterCommit;
    @Mock
    private WorkflowAiActionService workflowAiActionService;
    @Mock
    private ObjectProvider<WorkflowService> workflows;
    @Mock
    private WorkflowService workflowService;

    private WorkflowTaskExecutor executor() {
        return new WorkflowTaskExecutor(
                objects,
                natsEventBridge,
                functionService,
                eventService,
                bindingRefreshAfterCommit,
                workflowAiActionService,
                workflows
        );
    }

    private static WorkflowInstance instance() {
        return new WorkflowInstance("inst-1", WORKFLOW, "start");
    }

    private static ServiceTaskDefinition task(WorkflowActionType action, Map<String, String> params) {
        return new ServiceTaskDefinition("t1", "task", action, params);
    }

    @Test
    void setVariableWritesStringRecordToTargetObject() throws WorkflowException {
        executor().executeServiceTask(
                task(WorkflowActionType.SET_VARIABLE, Map.of("targetObject", TARGET, "variable", "mode", "value", "auto")),
                instance()
        );

        ArgumentCaptor<DataRecord> record = ArgumentCaptor.forClass(DataRecord.class);
        verify(objects).setVariableValue(eq(TARGET), eq("mode"), record.capture());
        assertEquals("auto", record.getValue().firstRow().get("value"));
    }

    @Test
    void missingRequiredParameterIsAWorkflowException() {
        WorkflowException ex = assertThrows(WorkflowException.class, () -> executor().executeServiceTask(
                task(WorkflowActionType.SET_VARIABLE, Map.of("variable", "mode")),
                instance()
        ));
        assertTrue(ex.getMessage().contains("targetObject"));
        verify(objects, never()).setVariableValue(anyString(), anyString(), any());
    }

    @Test
    void readVariableCopiesFieldIntoInstanceContext() throws WorkflowException {
        DataSchema schema = DataSchema.builder("v").field("value", FieldType.STRING).build();
        Variable variable = mock(Variable.class);
        when(variable.value()).thenReturn(Optional.of(DataRecord.single(schema, Map.of("value", "42"))));
        PlatformObject node = mock(PlatformObject.class);
        when(node.getVariable("level")).thenReturn(Optional.of(variable));
        when(objects.require(TARGET)).thenReturn(node);

        WorkflowInstance instance = instance();
        executor().executeServiceTask(
                task(WorkflowActionType.READ_VARIABLE, Map.of("objectPath", TARGET, "variable", "level", "contextKey", "tankLevel")),
                instance
        );

        assertEquals("42", instance.variables().get("tankLevel"));
    }

    @Test
    void invokeFunctionMapsInputOutputAndFailsOnErrorCode() {
        DataSchema out = DataSchema.builder("out")
                .field("error_code", FieldType.STRING)
                .field("ticket", FieldType.STRING)
                .build();
        when(functionService.invoke(eq(TARGET), eq("open"), any(DataRecord.class)))
                .thenReturn(DataRecord.single(out, Map.of("error_code", "E_LOCKED", "ticket", "T-7")));

        WorkflowInstance instance = instance();
        instance.setVariable("who", "op-1");
        WorkflowException ex = assertThrows(WorkflowException.class, () -> executor().executeServiceTask(
                task(WorkflowActionType.INVOKE_FUNCTION, Map.of(
                        "objectPath", TARGET,
                        "functionName", "open",
                        "inputMap", "operator=${who},reason=manual",
                        "outputMap", "ticket=ticket"
                )),
                instance
        ));

        assertTrue(ex.getMessage().contains("E_LOCKED"));
        // output was mapped before the error check, input map resolved ${who} and literals
        assertEquals("T-7", instance.variables().get("ticket"));
        ArgumentCaptor<DataRecord> input = ArgumentCaptor.forClass(DataRecord.class);
        verify(functionService).invoke(eq(TARGET), eq("open"), input.capture());
        assertEquals("op-1", input.getValue().firstRow().get("operator"));
        assertEquals("manual", input.getValue().firstRow().get("reason"));
        verify(bindingRefreshAfterCommit).refreshNow(TARGET, "open");
    }

    @Test
    void bpmnThrowMessageIsDeliveredThroughTheServiceProxy() throws WorkflowException {
        when(workflows.getObject()).thenReturn(workflowService);
        WorkflowInstance instance = instance();

        executor().executeMessageTask(
                new MessageTaskDefinition("m1", "msg", "order.ready", "", "bpmn-throw", Map.of()),
                instance
        );

        verify(workflowService).deliverMessageByWorkflowPath(WORKFLOW, "order.ready", null);
        verify(natsEventBridge, never()).publish(anyString(), anyString());
    }

    @Test
    void natsMessageTaskPublishesOnTheBridge() {
        executor().executeMessageTask(
                new MessageTaskDefinition("m1", "msg", "ispf.alerts", "hello", "nats", Map.of()),
                instance()
        );
        verify(natsEventBridge).publish("ispf.alerts", "hello");
    }

    @Test
    void callActivityPassesMappedInputAndParentLinkToTheChild() throws WorkflowException {
        when(workflows.getObject()).thenReturn(workflowService);
        WorkflowInstance parent = instance();
        parent.setVariable("orderId", "A-9");
        parent.setVariable("triggerObjectPath", TARGET);
        WorkflowInstance child = new WorkflowInstance("child-1", "root.platform.workflows.child", "start");
        when(workflowService.runWorkflowInstance(
                eq("root.platform.workflows.child"),
                eq(TARGET),
                eq(WorkflowStartTrigger.EVENT),
                any()
        )).thenReturn(child);

        CallActivityExecutor.Result result = executor().executeCallActivity(
                new CallActivityDefinition("c1", "call", "root.platform.workflows.child", Map.of("inputMap", "order=${orderId},src=bundle")),
                parent
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> input = ArgumentCaptor.forClass(Map.class);
        verify(workflowService).runWorkflowInstance(anyString(), eq(TARGET), any(), input.capture());
        assertEquals("A-9", input.getValue().get("order"));
        assertEquals("bundle", input.getValue().get("src"));
        assertEquals("inst-1", input.getValue().get("__callParentInstanceId"));
        assertEquals("child-1", result.childInstanceId());
        assertEquals(InstanceStatus.RUNNING, result.status());
    }

    @Test
    void parseIntFallsBackOnGarbage() {
        assertEquals(30, WorkflowTaskExecutor.parseInt("x", 30));
        assertEquals(7, WorkflowTaskExecutor.parseInt(" 7 ", 30));
    }

    @Test
    void fireEventWithoutPayloadVariablePassesNullPayload() throws WorkflowException {
        executor().executeServiceTask(
                task(WorkflowActionType.FIRE_EVENT, Map.of("objectPath", TARGET, "eventName", "alarm")),
                instance()
        );
        verify(eventService).publishFired(eq(TARGET), eq("alarm"), (DataRecord) isNull());
    }

    @Test
    void userTaskFunctionFailureRejectsComplete() {
        when(functionService.invoke(TARGET, "boom"))
                .thenThrow(new IllegalStateException("Expression returned empty: no_such_name"));

        UserTaskDefinition userTask = new UserTaskDefinition(
                "ut1",
                "Approve",
                "Approve",
                "",
                "operator",
                Map.of("function", "boom", "targetObject", TARGET)
        );

        WorkflowException ex = assertThrows(WorkflowException.class, () ->
                executor().executeUserTaskAction(userTask, TARGET));

        assertTrue(ex.getMessage().contains("boom"));
        assertTrue(ex.getMessage().contains(TARGET));
        assertTrue(ex.getMessage().contains("failed"));
    }

    @Test
    void userTaskWithoutFunctionIsNoOp() throws WorkflowException {
        UserTaskDefinition userTask = new UserTaskDefinition(
                "ut1",
                "Approve",
                "Approve",
                "",
                "operator",
                Map.of()
        );

        executor().executeUserTaskAction(userTask, TARGET);

        verify(functionService, never()).invoke(anyString(), anyString());
    }
}
