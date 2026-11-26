package com.ispf.server.correlator;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.EventCorrelatorService.CreateCorrelatorRequest;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCorrelatorServiceMissingEventTargetTest {

    private static final String DEVICE = "root.platform.devices.event-proof";

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject device;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void missingEventIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(DEVICE)).thenReturn(Optional.of(device));
        when(device.events()).thenReturn(Map.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request("noSuchEvent"))
        );

        assertEquals("Unknown event: noSuchEvent", error.getMessage());
        verify(automationTreeService, never()).createCorrelator(
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
                "event-proof",
                DEVICE,
                CorrelatorPatternType.COUNT,
                "event-probe",
                null,
                0,
                1,
                0,
                0,
                CorrelatorActionType.FIRE_EVENT,
                actionTarget,
                "",
                false
        );
    }
}
