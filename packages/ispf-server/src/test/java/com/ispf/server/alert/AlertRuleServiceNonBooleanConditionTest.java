package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.alert.AlertRuleService.CreateAlertRuleRequest;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceNonBooleanConditionTest {

    private static final String DEVICE = "root.platform.devices.condition-proof";

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
    void numericConditionIsRejectedBeforeSave() {
        when(objectManager.require(DEVICE)).thenReturn(device);
        when(device.getVariable("temp")).thenReturn(Optional.of(watchVariable));

        ExpressionException error = assertThrows(
                ExpressionException.class,
                () -> service().create(request("1"))
        );

        assertTrue(error.getMessage().contains("must return a boolean"));
        verifyNoInteractions(automationTreeService);
    }

    @Test
    void booleanComparisonStaysValid() {
        assertDoesNotThrow(() -> expressionEngine.requireBooleanAlertCondition("self.temp[\"value\"] > 1000"));
        assertDoesNotThrow(() -> expressionEngine.requireBooleanAlertCondition("true"));
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

    private static CreateAlertRuleRequest request(String conditionExpr) {
        return new CreateAlertRuleRequest(
                "condition-proof",
                DEVICE,
                "temp",
                conditionExpr,
                "condition-proof",
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
}
