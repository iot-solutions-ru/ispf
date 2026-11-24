package com.ispf.server.alert;

import com.ispf.server.alert.AlertRuleService.CreateAlertRuleRequest;
import com.ispf.server.automation.AutomationTreeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceNegativeTimingTest {

    @Mock
    private AutomationTreeService automationTreeService;

    @Test
    void negativeDelayIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(-5, 0, 0))
        );

        assertEquals("delaySeconds must not be negative", exception.getMessage());
        verifyNoInteractions(automationTreeService);
    }

    @Test
    void negativePollIntervalIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(0, -1, 0))
        );

        assertEquals("pollIntervalMs must not be negative", exception.getMessage());
        verifyNoInteractions(automationTreeService);
    }

    @Test
    void negativeDeactivateDelayIsRejectedBeforeSave() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(0, 0, -5))
        );

        assertEquals("deactivateDelaySeconds must not be negative", exception.getMessage());
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

    private static CreateAlertRuleRequest request(int delaySeconds, int pollIntervalMs, int deactivateDelaySeconds) {
        return new CreateAlertRuleRequest(
                "alert-proof",
                "root.platform.devices.alert-proof",
                "temp",
                "self.temp[\"value\"] > 1000",
                "alert-probe",
                null,
                false,
                false,
                delaySeconds,
                false,
                "HIGH",
                false,
                null,
                null,
                null,
                null,
                deactivateDelaySeconds,
                pollIntervalMs,
                null,
                null
        );
    }
}
