package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.alert.AlertRuleService.CreateAlertRuleRequest;
import com.ispf.server.alert.AlertRuleService.UpdateAlertRuleRequest;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceMissingWatchVariableTest {

    private static final String RULE = "root.platform.alert-rules.watch-proof";
    private static final String DEVICE = "root.platform.devices.watch-proof";

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private PlatformObject device;

    @Test
    void missingWatchVariableIsRejectedBeforeSave() {
        when(automationTreeService.getAlertRule(RULE)).thenReturn(stored("temp"));
        when(objectManager.require(DEVICE)).thenReturn(device);
        when(device.getVariable("missing")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().update(RULE, update("missing", null))
        );

        assertEquals("Unknown watchVariable: missing", error.getMessage());
        verify(automationTreeService, never()).updateAlertRule(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void blankWatchVariableIsRejectedBeforeSave() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(create(" "))
        );

        assertEquals("watchVariable is required", error.getMessage());
        verify(automationTreeService, never()).createAlertRule(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void missingPayloadVariableIsRejectedBeforeSave() {
        when(automationTreeService.getAlertRule(RULE)).thenReturn(stored("temp"));
        when(objectManager.require(DEVICE)).thenReturn(device);
        when(device.getVariable("missing")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().update(RULE, update(null, "missing"))
        );

        assertEquals("Unknown payloadVariable: missing", error.getMessage());
        verify(automationTreeService, never()).updateAlertRule(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private AlertRuleService service() {
        return new AlertRuleService(
                automationTreeService,
                objectManager,
                null,
                null,
                null,
                null,
                notificationDispatchService,
                null,
                null
        );
    }

    private static AlertRule stored(String watchVariable) {
        Instant now = Instant.parse("2026-11-25T00:00:00Z");
        return new AlertRule(
                RULE,
                "watch-proof",
                DEVICE,
                watchVariable,
                "true",
                "watch-proof",
                null,
                false,
                false,
                0,
                false,
                0,
                "HIGH",
                false,
                null,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                null,
                null,
                null,
                null
        );
    }

    private static CreateAlertRuleRequest create(String watchVariable) {
        return new CreateAlertRuleRequest(
                "watch-proof",
                DEVICE,
                watchVariable,
                "true",
                "watch-proof",
                null,
                false,
                false,
                0,
                false,
                "HIGH",
                false,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null
        );
    }

    private static UpdateAlertRuleRequest update(String watchVariable, String payloadVariable) {
        return new UpdateAlertRuleRequest(
                null,
                null,
                watchVariable,
                null,
                null,
                payloadVariable,
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
                null,
                null,
                null
        );
    }
}
