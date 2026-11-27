package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.alert.AlertRuleService.CreateAlertRuleRequest;
import com.ispf.server.alert.AlertRuleService.UpdateAlertRuleRequest;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceInvalidTriggerMessageTest {

    private static final String DEVICE = "root.platform.devices.trigger-proof";
    private static final String RULE = "root.platform.alert-rules.trigger-proof";

    @Mock
    private AutomationTreeService automationTreeService;

    @Mock
    private ObjectManager objectManager;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private ExpressionFormalVerificationService formalVerificationService;

    @Mock
    private PlatformObject device;

    @Mock
    private Variable watchVariable;

    private final ExpressionEngine expressionEngine = new ExpressionEngine();

    @Test
    void unreadableTriggerMessageIsRejectedBeforeSave() {
        when(objectManager.require(DEVICE)).thenReturn(device);
        when(device.getVariable("temperature")).thenReturn(Optional.of(watchVariable));

        ExpressionException brace = assertThrows(
                ExpressionException.class,
                () -> service().create(createRequest("{"))
        );
        ExpressionException plus = assertThrows(
                ExpressionException.class,
                () -> service().create(createRequest("1 +"))
        );

        assertEquals("Invalid expression: {", brace.getMessage());
        assertEquals("Invalid expression: 1 +", plus.getMessage());
        verifyNoInteractions(automationTreeService);
    }

    @Test
    void blankAndNullTriggerMessageAreAcceptedOnCreate() {
        stubCreatePath(null);
        assertDoesNotThrow(() -> service().create(createRequest(null)));

        stubCreatePath("");
        assertDoesNotThrow(() -> service().create(createRequest("")));

        stubCreatePath("   ");
        assertDoesNotThrow(() -> service().create(createRequest("   ")));
    }

    @Test
    void readableLiteralIsAcceptedOnCreateAndUpdate() {
        stubCreatePath("'alarm'");
        assertDoesNotThrow(() -> service().create(createRequest("'alarm'")));
        verify(automationTreeService).createAlertRule(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                isNull(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(),
                anyString(), anyBoolean(), isNull(), isNull(), isNull(), isNull(),
                anyInt(), anyInt(), eq("'alarm'"), isNull()
        );

        when(automationTreeService.getAlertRule(RULE)).thenReturn(stored("'alarm'"));
        when(automationTreeService.updateAlertRule(
                eq(RULE), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("'alarm'"), isNull()
        )).thenReturn(stored("'alarm'"));

        assertDoesNotThrow(() -> service().update(RULE, updateRequest("'alarm'")));
        verify(automationTreeService).updateAlertRule(
                eq(RULE), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("'alarm'"), isNull()
        );
    }

    @Test
    void unreadableTriggerMessageIsRejectedOnUpdate() {
        when(automationTreeService.getAlertRule(RULE)).thenReturn(stored("'alarm'"));

        ExpressionException brace = assertThrows(
                ExpressionException.class,
                () -> service().update(RULE, updateRequest("{"))
        );
        assertEquals("Invalid expression: {", brace.getMessage());
        verify(automationTreeService, never()).updateAlertRule(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(),
                any(), any()
        );
    }

    private void stubCreatePath(String triggerMessage) {
        when(objectManager.require(DEVICE)).thenReturn(device);
        when(device.getVariable("temperature")).thenReturn(Optional.of(watchVariable));
        if (triggerMessage == null) {
            when(automationTreeService.createAlertRule(
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    isNull(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(),
                    anyString(), anyBoolean(), isNull(), isNull(), isNull(), isNull(),
                    anyInt(), anyInt(), isNull(), isNull()
            )).thenReturn(stored(null));
        } else {
            when(automationTreeService.createAlertRule(
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    isNull(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(),
                    anyString(), anyBoolean(), isNull(), isNull(), isNull(), isNull(),
                    anyInt(), anyInt(), eq(triggerMessage), isNull()
            )).thenReturn(stored(triggerMessage));
        }
        when(automationTreeService.getAlertRule(RULE)).thenReturn(stored(triggerMessage));
    }

    private AlertRuleService service() {
        return new AlertRuleService(
                automationTreeService,
                objectManager,
                expressionEngine,
                formalVerificationService,
                null,
                null,
                notificationDispatchService,
                null,
                null
        );
    }

    private static CreateAlertRuleRequest createRequest(String triggerMessage) {
        return new CreateAlertRuleRequest(
                "trigger-proof",
                DEVICE,
                "temperature",
                "true",
                "thresholdExceeded",
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
                triggerMessage,
                null
        );
    }

    private static UpdateAlertRuleRequest updateRequest(String triggerMessage) {
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                triggerMessage,
                null
        );
    }

    private static AlertRule stored(String triggerMessage) {
        Instant now = Instant.parse("2026-11-25T00:00:00Z");
        return new AlertRule(
                RULE,
                "trigger-proof",
                DEVICE,
                "temperature",
                "true",
                "thresholdExceeded",
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
                triggerMessage,
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
}
