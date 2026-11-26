package com.ispf.server.alert;

import com.ispf.server.alert.AlertRuleService.UpdateAlertRuleRequest;
import com.ispf.server.automation.AutomationTreeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceNegativeRateLimitTest {

    @Mock
    private AutomationTreeService automationTreeService;

    @Test
    void negativeRateLimitIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().update("root.platform.alert-rules.rate-proof", request(-5))
        );

        assertEquals("rateLimitSeconds must not be negative", exception.getMessage());
        verifyNoInteractions(automationTreeService);
    }

    private AlertRuleService service() {
        return new AlertRuleService(
                automationTreeService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static UpdateAlertRuleRequest request(int rateLimitSeconds) {
        return new UpdateAlertRuleRequest(
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
                rateLimitSeconds,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
