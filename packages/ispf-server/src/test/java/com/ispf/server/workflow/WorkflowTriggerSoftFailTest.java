package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.spi.WorkflowStartTrigger;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTriggerSoftFailTest {

    private static final String OBJECT_PATH = "root.platform.devices.demo-sensor-01";
    private static final String STALE_WORKFLOW = "root.platform.workflows.deleted-demo";
    private static final String ACTIVE_WORKFLOW = "root.platform.workflows.active-demo";
    private static final String VARIABLE_NAME = "temperature";
    private static final String EVENT_NAME = "thresholdExceeded";

    @Mock
    private WorkflowObjectAccess objects;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private WorkflowInstanceStore instanceStore;
    @Mock
    private WorkflowConditionFactory conditionFactory;
    @Mock
    private WorkflowTaskExecutor taskExecutor;
    @Mock
    private WorkflowInstanceStatePublisher statePublisher;
    @Mock
    private WorkflowInstanceRepository instanceRepository;
    @Mock
    private WorkflowEventTriggerIndex eventTriggerIndex;
    @Mock
    private AutomationMetricsRecorder automationMetricsRecorder;
    @Mock
    private WorkflowTriggerIndexRefresh triggerIndexRefresh;
    @Mock
    private ObjectProvider<WorkflowService> self;
    @Mock
    private WorkflowDeadLetterService deadLetterService;
    @Mock
    private WorkflowWebhookIndex webhookIndex;
    @Mock
    private WorkflowRetryService retryService;
    @Mock
    private ExpressionFormalVerificationService formalVerificationService;

    private WorkflowService workflowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService(
                objects,
                workflowEngine,
                objectMapper,
                instanceStore,
                conditionFactory,
                taskExecutor,
                statePublisher,
                instanceRepository,
                eventTriggerIndex,
                automationMetricsRecorder,
                triggerIndexRefresh,
                self,
                deadLetterService,
                webhookIndex,
                retryService,
                formalVerificationService
        );
    }

    @Test
    void variableTriggerSkipsMissingWorkflowAndContinues() {
        PlatformObject activeNode = workflowNode(WorkflowLifecycleStatus.DRAFT);
        when(eventTriggerIndex.findVariableWorkflows(OBJECT_PATH, VARIABLE_NAME))
                .thenReturn(List.of(STALE_WORKFLOW, ACTIVE_WORKFLOW));
        when(objects.require(STALE_WORKFLOW))
                .thenThrow(new ObjectNotFoundException(STALE_WORKFLOW));
        when(objects.require(ACTIVE_WORKFLOW)).thenReturn(activeNode);

        assertThatCode(() -> workflowService.handleVariableTrigger(OBJECT_PATH, VARIABLE_NAME))
                .doesNotThrowAnyException();

        verify(objects).require(STALE_WORKFLOW);
        verify(objects).require(ACTIVE_WORKFLOW);
        verify(eventTriggerIndex).removeWorkflow(STALE_WORKFLOW);
    }

    @Test
    void eventTriggerSkipsMissingWorkflowAndContinues() {
        PlatformObject activeNode = workflowNode(WorkflowLifecycleStatus.DRAFT);
        when(eventTriggerIndex.findEventWorkflows(OBJECT_PATH, EVENT_NAME))
                .thenReturn(List.of(STALE_WORKFLOW, ACTIVE_WORKFLOW));
        when(objects.require(STALE_WORKFLOW))
                .thenThrow(new ObjectNotFoundException(STALE_WORKFLOW));
        when(objects.require(ACTIVE_WORKFLOW)).thenReturn(activeNode);

        assertThatCode(() -> workflowService.handleEventTrigger(OBJECT_PATH, EVENT_NAME))
                .doesNotThrowAnyException();

        verify(objects).require(STALE_WORKFLOW);
        verify(objects).require(eq(ACTIVE_WORKFLOW));
        verify(eventTriggerIndex).removeWorkflow(STALE_WORKFLOW);
    }

    @Test
    void variableTriggerRecordsTheStartFailureAndDoesNotReturnSuccess() throws Exception {
        PlatformObject node = workflowNode(WorkflowLifecycleStatus.ACTIVE);
        Variable triggerJson = stringVariable(
                "triggerJson",
                "{\"triggerType\":\"variable\",\"objectPath\":\"" + OBJECT_PATH
                        + "\",\"variableName\":\"" + VARIABLE_NAME + "\"}"
        );
        when(node.path()).thenReturn(ACTIVE_WORKFLOW);
        when(node.getVariable("triggerJson")).thenReturn(Optional.of(triggerJson));
        when(eventTriggerIndex.findVariableWorkflows(OBJECT_PATH, VARIABLE_NAME))
                .thenReturn(List.of(ACTIVE_WORKFLOW));
        when(objects.require(ACTIVE_WORKFLOW)).thenReturn(node);
        WorkflowService spyService = spy(workflowService);
        doThrow(new WorkflowException("bpmn boom")).when(spyService).runWorkflow(
                eq(ACTIVE_WORKFLOW),
                eq(OBJECT_PATH),
                eq(WorkflowStartTrigger.VARIABLE)
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> spyService.handleVariableTrigger(OBJECT_PATH, VARIABLE_NAME)
        );

        assertThat(error.getMessage()).contains("bpmn boom");
        verify(deadLetterService).recordCommitted(
                eq("trigger-start"),
                eq(ACTIVE_WORKFLOW),
                eq(1),
                eq("bpmn boom"),
                contains("\"trigger\":\"variable\"")
        );
    }

    @Test
    void eventTriggerRecordsTheStartFailureAndDoesNotReturnSuccess() throws Exception {
        PlatformObject node = workflowNode(WorkflowLifecycleStatus.ACTIVE);
        when(eventTriggerIndex.findEventWorkflows(OBJECT_PATH, EVENT_NAME))
                .thenReturn(List.of(ACTIVE_WORKFLOW));
        when(objects.require(ACTIVE_WORKFLOW)).thenReturn(node);
        WorkflowService spyService = spy(workflowService);
        doThrow(new WorkflowException("bpmn boom")).when(spyService).runWorkflow(
                eq(ACTIVE_WORKFLOW),
                eq(OBJECT_PATH),
                eq(WorkflowStartTrigger.EVENT)
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> spyService.handleEventTrigger(OBJECT_PATH, EVENT_NAME)
        );

        assertThat(error.getMessage()).contains("bpmn boom");
        verify(deadLetterService).recordCommitted(
                eq("trigger-start"),
                eq(ACTIVE_WORKFLOW),
                eq(1),
                eq("bpmn boom"),
                contains("\"trigger\":\"event\"")
        );
    }

    private Variable stringVariable(String name, String value) {
        Variable variable = mock(Variable.class);
        when(variable.value()).thenReturn(Optional.of(DataRecord.single(
                DataSchema.builder(name).field("value", FieldType.STRING).build(),
                Map.of("value", value)
        )));
        return variable;
    }

    private PlatformObject workflowNode(WorkflowLifecycleStatus status) {
        PlatformObject node = mock(PlatformObject.class);
        Variable statusVariable = mock(Variable.class);
        when(statusVariable.value()).thenReturn(Optional.of(DataRecord.single(
                DataSchema.builder("status").field("value", FieldType.STRING).build(),
                Map.of("value", status.name())
        )));
        when(node.getVariable("status")).thenReturn(Optional.of(statusVariable));
        return node;
    }
}
