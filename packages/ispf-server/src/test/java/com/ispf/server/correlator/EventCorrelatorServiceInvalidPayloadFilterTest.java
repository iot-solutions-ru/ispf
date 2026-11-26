package com.ispf.server.correlator;

import com.ispf.expression.ExpressionEngine;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.EventCorrelatorService.CreateCorrelatorRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventCorrelatorServiceInvalidPayloadFilterTest {

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void unreadablePayloadFilterIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request("1 +"))
        );

        assertTrue(exception.getMessage().startsWith("payloadFilterExpr: Invalid expression: 1 +"));
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

    @Test
    void readablePayloadFilterIsSaved() {
        service().create(request("true"));

        verify(automationTreeService).createCorrelator(
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
                org.mockito.ArgumentMatchers.eq("true"),
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
                new ExpressionEngine(),
                null,
                null,
                null,
                null,
                null,
                null,
                transactionManager
        );
    }

    private static CreateCorrelatorRequest request(String payloadFilterExpr) {
        return new CreateCorrelatorRequest(
                "corr-proof",
                "root.platform.devices",
                CorrelatorPatternType.COUNT,
                "corr-probe",
                null,
                60,
                1,
                0,
                0,
                CorrelatorActionType.SEND_WEBHOOK,
                "http://127.0.0.1:9/hook",
                payloadFilterExpr,
                false
        );
    }
}
