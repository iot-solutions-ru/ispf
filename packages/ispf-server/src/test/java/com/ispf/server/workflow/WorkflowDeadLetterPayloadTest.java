package com.ispf.server.workflow;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDeadLetterPayloadTest {

    private static final String WORKFLOW = "root.platform.workflows.dead-letter-payload";

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

    @Test
    void serializationFailureDoesNotStoreAnEmptyDeadLetter() {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new IllegalStateException("cycle"));
        PlatformObject node = mock(PlatformObject.class);
        when(node.getVariable(anyString())).thenReturn(Optional.empty());
        when(objects.require(WORKFLOW)).thenReturn(node);
        WorkflowService workflowService = new WorkflowService(
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
        WorkflowInstance instance = WorkflowInstance.restore(
                "inst-dlq",
                WORKFLOW,
                InstanceStatus.FAILED,
                "done",
                Instant.parse("2026-10-06T08:00:00Z"),
                Instant.parse("2026-10-06T08:00:01Z"),
                null,
                null,
                List.of("START"),
                Map.of("lastAction", "should-fail"),
                "Missing service task parameter: targetObject",
                null
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> workflowService.finishStep(null, instance, mock(BpmnProcess.class), Map.of("action", "setVariable"))
        );

        assertThat(error.getMessage()).contains("Failed to serialize workflow dead letter");
        verify(deadLetterService, never()).record(anyString(), anyString(), anyInt(), any(), any());
    }
}
