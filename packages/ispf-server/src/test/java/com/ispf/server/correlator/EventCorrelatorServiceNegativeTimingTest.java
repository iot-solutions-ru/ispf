package com.ispf.server.correlator;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.EventCorrelatorService.CreateCorrelatorRequest;
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

@ExtendWith(MockitoExtension.class)
class EventCorrelatorServiceNegativeTimingTest {

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void negativeCooldownIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(-5, 0))
        );

        assertEquals("cooldownSeconds must not be negative", exception.getMessage());
        verifyNeverCreated();
    }

    @Test
    void negativeSequenceGapIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(0, -3))
        );

        assertEquals("sequenceGapSeconds must not be negative", exception.getMessage());
        verifyNeverCreated();
    }

    private void verifyNeverCreated() {
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
                null,
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

    private static CreateCorrelatorRequest request(int cooldownSeconds, int sequenceGapSeconds) {
        return new CreateCorrelatorRequest(
                "corr-proof",
                "root.platform.devices",
                CorrelatorPatternType.COUNT,
                "corr-probe",
                null,
                60,
                1,
                cooldownSeconds,
                sequenceGapSeconds,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.none",
                "",
                false
        );
    }
}
