package com.ispf.server.alert;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.event.EventService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.ml.AnomalyAlertRuleEvaluator;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleConditionHonestyTest {

    private static final String RULE_ID = "root.automation.alert-rules.honesty";
    private static final String TARGET = "root.platform.devices.pump";

    @Mock
    AutomationTreeService automationTreeService;
    @Mock
    ObjectManager objectManager;
    @Mock
    ExpressionEngine expressionEngine;
    @Mock
    ExpressionFormalVerificationService formalVerificationService;
    @Mock
    EventService eventService;
    @Mock
    AutomationMetricsRecorder automationMetricsRecorder;
    @Mock
    NotificationDispatchService notificationDispatchService;
    @Mock
    AlarmShelfService alarmShelfService;
    @Mock
    AnomalyAlertRuleEvaluator anomalyAlertRuleEvaluator;

    private AlertRuleService service;

    @BeforeEach
    void setUp() {
        service = new AlertRuleService(
                automationTreeService,
                objectManager,
                expressionEngine,
                formalVerificationService,
                eventService,
                automationMetricsRecorder,
                notificationDispatchService,
                alarmShelfService,
                anomalyAlertRuleEvaluator
        );
    }

    @Test
    void uncomputableConditionFailsInsteadOfActingAsFalse() {
        AlertRule rule = sampleRule("no_such_name");
        PlatformObject target = deviceWithTemperature(90.0);
        when(automationTreeService.getAlertRule(RULE_ID)).thenReturn(rule);
        when(objectManager.require(TARGET)).thenReturn(target);
        when(expressionEngine.evaluateAlertCondition(eq("no_such_name"), eq(target), eq("temperature")))
                .thenThrow(new ExpressionException("unknown name"));

        assertThatCode(() -> service.evaluateRule(rule)).doesNotThrowAnyException();

        verify(eventService, never()).fireAutomation(anyString(), anyString(), any());
        verify(automationTreeService, never()).setAlertRuleLastConditionMet(anyString(), anyBoolean());
        verify(automationTreeService, never()).setAlertRuleEnabled(anyString(), anyBoolean());
    }

    @Test
    void anomalyHistoryFailureIsNotTreatedAsFalse() {
        AlertRule rule = anomalyRule();
        when(automationTreeService.getAlertRule(RULE_ID)).thenReturn(rule);
        when(objectManager.require(TARGET)).thenReturn(deviceWithTemperature(90.0));
        when(anomalyAlertRuleEvaluator.evaluate(TARGET, "temperature", "threshold-v1"))
                .thenThrow(new IllegalStateException(
                        "Anomaly history read failed for " + TARGET + "/temperature: history store down",
                        new IllegalStateException("history store down")
                ));

        assertThatCode(() -> service.evaluateRule(rule)).doesNotThrowAnyException();

        verify(eventService, never()).fireAutomation(anyString(), anyString(), any());
        verify(automationTreeService, never()).setAlertRuleLastConditionMet(anyString(), anyBoolean());
        verify(automationTreeService, never()).setAlertRuleEnabled(anyString(), anyBoolean());
    }

    @Test
    void falseConditionRemainsFalseAndDoesNotFire() {
        AlertRule rule = sampleRule("false");
        PlatformObject target = deviceWithTemperature(90.0);
        when(automationTreeService.getAlertRule(RULE_ID)).thenReturn(rule);
        when(objectManager.require(TARGET)).thenReturn(target);
        when(expressionEngine.evaluateAlertCondition(eq("false"), eq(target), eq("temperature")))
                .thenReturn(false);
        when(objectManager.require(RULE_ID)).thenReturn(alertNode());

        assertThatCode(() -> service.evaluateRule(rule)).doesNotThrowAnyException();

        verify(eventService, never()).fireAutomation(anyString(), anyString(), any());
        verify(automationTreeService).setAlertRuleLastConditionMet(RULE_ID, false);
    }

    private static PlatformObject alertNode() {
        PlatformObject node = new PlatformObject(RULE_ID, RULE_ID, ObjectType.ALERT, "honesty", "", null);
        node.addEvent(new EventDescriptor("raise", "raise", DataSchema.builder("payload").build(), EventLevel.WARNING));
        return node;
    }

    private static PlatformObject deviceWithTemperature(double value) {
        PlatformObject node = new PlatformObject(TARGET, TARGET, ObjectType.DEVICE, "pump", "", null);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        DataRecord initial = DataRecord.single(schema, java.util.Map.of("value", value));
        node.addVariable(new Variable("temperature", schema, true, true, initial));
        return node;
    }

    private static AlertRule sampleRule(String conditionExpr) {
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        return new AlertRule(
                RULE_ID,
                "honesty",
                TARGET,
                "temperature",
                conditionExpr,
                "raise",
                null,
                true,
                true,
                0,
                false,
                0,
                "HIGH",
                false,
                null,
                0,
                1000,
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
                null
        );
    }

    private static AlertRule anomalyRule() {
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        return new AlertRule(
                RULE_ID,
                "honesty",
                TARGET,
                "temperature",
                "",
                "raise",
                null,
                true,
                true,
                0,
                false,
                0,
                "HIGH",
                false,
                null,
                0,
                1000,
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
                "threshold-v1"
        );
    }
}
