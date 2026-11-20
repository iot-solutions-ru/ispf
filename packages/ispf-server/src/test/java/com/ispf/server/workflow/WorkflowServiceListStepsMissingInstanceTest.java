package com.ispf.server.workflow;

import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.spi.WorkflowConditionCheck;
import com.ispf.server.spi.WorkflowMetrics;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceListStepsMissingInstanceTest {

    @Mock
    private WorkflowObjectAccess objects;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private ObjectMapper objectMapper;
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
    private WorkflowMetrics automationMetricsRecorder;
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
    private WorkflowConditionCheck formalVerificationService;

    @Test
    void missingInstanceIsRejectedBeforeReadingSteps() throws WorkflowException {
        when(instanceStore.exists("no-such-instance-716")).thenReturn(false);

        WorkflowInstanceNotFoundException exception = assertThrows(
                WorkflowInstanceNotFoundException.class,
                () -> service().listSteps("no-such-instance-716")
        );

        assertEquals("Workflow instance not found: no-such-instance-716", exception.getMessage());
        verify(instanceStore, never()).listSteps("no-such-instance-716");
        verify(instanceStore, never()).load("no-such-instance-716");
    }

    @Test
    void existingInstanceWithoutStepsReturnsEmpty() throws WorkflowException {
        when(instanceStore.exists("present")).thenReturn(true);
        when(instanceStore.listSteps("present")).thenReturn(List.of());

        assertEquals(List.of(), service().listSteps("present"));
        verify(instanceStore, never()).load("present");
    }

    private WorkflowService service() {
        return new WorkflowService(
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
}