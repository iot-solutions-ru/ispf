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
class EventCorrelatorServiceMissingReportTargetTest {

    private static final String MISSING = "root.platform.reports.report-proof-missing";

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void missingReportIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(MISSING)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(MISSING))
        );

        assertEquals("Unknown report: " + MISSING, error.getMessage());
        verify(automationTreeService, never()).createCorrelator(
                any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyInt(), anyInt(),
                any(), any(), any(), anyBoolean()
        );
    }

    @Test
    void existingReportIsSaved() {
        when(objectManager.tree()).thenReturn(objectTree);
        PlatformObject node = mock(PlatformObject.class);
        when(node.type()).thenReturn(ObjectType.REPORT);
        when(objectTree.findByPath("root.platform.reports.report-proof")).thenReturn(Optional.of(node));

        service().create(request("root.platform.reports.report-proof"));

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
                "report-proof",
                null,
                CorrelatorPatternType.COUNT,
                "report-probe",
                null,
                0,
                1,
                0,
                0,
                CorrelatorActionType.OPEN_OPERATOR_REPORT,
                actionTarget,
                "",
                false
        );
    }
}
