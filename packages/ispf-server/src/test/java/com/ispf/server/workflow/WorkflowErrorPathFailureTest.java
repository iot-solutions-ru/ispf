package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.spi.WorkflowStartTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowErrorPathFailureTest {

    private static final String PRIMARY = "root.platform.workflows.error-path-primary";
    private static final String ERROR_PATH = "root.platform.workflows.error-path-child";

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
    void failedErrorPathIsRecordedOnTheCompensationWorkflow() throws Exception {
        PlatformObject node = mock(PlatformObject.class);
        Variable errorPath = stringVariable("errorWorkflowPath", ERROR_PATH);
        when(objects.require(PRIMARY)).thenReturn(node);
        when(node.getVariable(any())).thenReturn(Optional.empty());
        when(node.getVariable("errorWorkflowPath")).thenReturn(Optional.of(errorPath));
        WorkflowService compensation = spy(workflowService);
        when(self.getObject()).thenReturn(compensation);
        doThrow(new WorkflowException("bpmn empty")).when(compensation).runWorkflow(
                eq(ERROR_PATH),
                eq(PRIMARY),
                eq(WorkflowStartTrigger.EVENT),
                any()
        );
        WorkflowInstance instance = new WorkflowInstance("inst-error-path", PRIMARY, "start");
        instance.fail("gateway boom");

        workflowService.finishStep(PRIMARY, instance, mock(BpmnProcess.class), Map.of());

        assertThat(instance.errorMessage())
                .contains("gateway boom")
                .contains("Workflow error path failed: " + ERROR_PATH + ": bpmn empty");
        verify(deadLetterService).recordCommitted(
                eq("inst-error-path"),
                eq(ERROR_PATH),
                eq(1),
                eq("Workflow error path failed: " + ERROR_PATH + ": bpmn empty"),
                contains("\"failedWorkflowPath\":\"" + PRIMARY + "\"")
        );
        verify(statePublisher, times(2)).publish(PRIMARY, instance);
    }

    private Variable stringVariable(String name, String value) {
        Variable variable = mock(Variable.class);
        when(variable.value()).thenReturn(Optional.of(DataRecord.single(
                DataSchema.builder(name).field("value", FieldType.STRING).build(),
                Map.of("value", value)
        )));
        return variable;
    }
}
