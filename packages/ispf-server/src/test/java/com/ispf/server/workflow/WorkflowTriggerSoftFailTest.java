package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.binding.BindingRefreshAfterCommit;
import com.ispf.server.cluster.NatsEventBridge;
import com.ispf.server.event.EventService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private ObjectManager objectManager;
    @Mock
    private SystemObjectStructureService structureService;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private NatsEventBridge natsEventBridge;
    @Mock
    private WorkflowInstanceStore instanceStore;
    @Mock
    private WorkflowConditionFactory conditionFactory;
    @Mock
    private FunctionService functionService;
    @Mock
    private EventService eventService;
    @Mock
    private WorkQueueService workQueueService;
    @Mock
    private WorkflowInstanceRepository instanceRepository;
    @Mock
    private BindingRefreshAfterCommit bindingRefreshAfterCommit;
    @Mock
    private WorkflowEventTriggerIndex eventTriggerIndex;
    @Mock
    private AutomationMetricsRecorder automationMetricsRecorder;
    @Mock
    private WorkflowTriggerIndexRefresh triggerIndexRefresh;
    @Mock
    private ObjectProvider<WorkflowService> self;
    @Mock
    private WorkflowAiActionService workflowAiActionService;
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
                objectManager,
                structureService,
                workflowEngine,
                natsEventBridge,
                objectMapper,
                instanceStore,
                conditionFactory,
                functionService,
                eventService,
                workQueueService,
                instanceRepository,
                bindingRefreshAfterCommit,
                eventTriggerIndex,
                automationMetricsRecorder,
                triggerIndexRefresh,
                self,
                workflowAiActionService,
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
        when(objectManager.require(STALE_WORKFLOW))
                .thenThrow(new ObjectNotFoundException(STALE_WORKFLOW));
        when(objectManager.require(ACTIVE_WORKFLOW)).thenReturn(activeNode);

        assertThatCode(() -> workflowService.handleVariableTrigger(OBJECT_PATH, VARIABLE_NAME))
                .doesNotThrowAnyException();

        verify(objectManager).require(STALE_WORKFLOW);
        verify(objectManager).require(ACTIVE_WORKFLOW);
        verify(eventTriggerIndex).removeWorkflow(STALE_WORKFLOW);
    }

    @Test
    void eventTriggerSkipsMissingWorkflowAndContinues() {
        PlatformObject activeNode = workflowNode(WorkflowLifecycleStatus.DRAFT);
        when(eventTriggerIndex.findEventWorkflows(OBJECT_PATH, EVENT_NAME))
                .thenReturn(List.of(STALE_WORKFLOW, ACTIVE_WORKFLOW));
        when(objectManager.require(STALE_WORKFLOW))
                .thenThrow(new ObjectNotFoundException(STALE_WORKFLOW));
        when(objectManager.require(ACTIVE_WORKFLOW)).thenReturn(activeNode);

        assertThatCode(() -> workflowService.handleEventTrigger(OBJECT_PATH, EVENT_NAME))
                .doesNotThrowAnyException();

        verify(objectManager).require(STALE_WORKFLOW);
        verify(objectManager).require(eq(ACTIVE_WORKFLOW));
        verify(eventTriggerIndex).removeWorkflow(STALE_WORKFLOW);
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
