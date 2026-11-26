package com.ispf.server.correlator;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.EventCorrelatorService.CreateCorrelatorRequest;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCorrelatorServiceMissingWorkflowTargetTest {

    private static final String MISSING = "root.platform.workflows.workflow-proof-missing";

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void missingWorkflowIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(MISSING)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(MISSING))
        );

        assertEquals("Unknown workflow: " + MISSING, exception.getMessage());
        verify(automationTreeService, never()).createCorrelator(
                any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyInt(), anyInt(),
                any(), any(), any(), anyBoolean()
        );
    }

    @Test
    void existingWorkflowIsSaved() {
        when(objectManager.tree()).thenReturn(objectTree);
        PlatformObject node = mock(PlatformObject.class);
        when(node.type()).thenReturn(ObjectType.WORKFLOW);
        when(objectTree.findByPath("root.platform.workflows.workflow-proof")).thenReturn(Optional.of(node));

        service().create(request("root.platform.workflows.workflow-proof"));

        verify(automationTreeService).createCorrelator(
                any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyInt(), anyInt(),
                any(), any(), any(), anyBoolean()
        );
    }

    private EventCorrelatorService service() {
        return new EventCorrelatorService(
                automationTreeService,
                null,
                null,
                null,
                null,
                objectManager,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                transactionManager
        );
    }

    private static CreateCorrelatorRequest request(String actionTarget) {
        return new CreateCorrelatorRequest(
                "workflow-proof",
                null,
                CorrelatorPatternType.COUNT,
                "workflow-probe",
                null,
                0,
                1,
                0,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                actionTarget,
                "",
                false
        );
    }
}
