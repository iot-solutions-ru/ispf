package com.ispf.server.correlator;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.EventCorrelatorService.CreateCorrelatorRequest;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCorrelatorServiceMissingSourceEventTest {

    private static final String PATH = "root.platform.devices.evt-proof";

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        ObjectTree tree = new ObjectTree();
        tree.register(new PlatformObject("evt-proof", PATH, ObjectType.DEVICE, "evt-proof", "", null));
        when(objectManager.tree()).thenReturn(tree);
    }

    @Test
    void missingSourceEventIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request("noSuchEvent"))
        );

        assertEquals("Unknown event: noSuchEvent", exception.getMessage());
        verify(automationTreeService, never()).createCorrelator(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                any(),
                anyBoolean()
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

    private static CreateCorrelatorRequest request(String eventName) {
        return new CreateCorrelatorRequest(
                "evt-proof",
                PATH,
                CorrelatorPatternType.COUNT,
                eventName,
                null,
                0,
                1,
                0,
                0,
                CorrelatorActionType.SEND_WEBHOOK,
                "http://127.0.0.1/hook",
                "",
                false
        );
    }
}
