package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.event.EventService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.ml.AnomalyAlertRuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AlertRuleService {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleService.class);

    private final AutomationTreeService automationTreeService;
    private final ObjectManager objectManager;
    private final ExpressionEngine expressionEngine;
    private final EventService eventService;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final NotificationDispatchService notificationDispatchService;

    private final AlarmShelfService alarmShelfService;
    private final AnomalyAlertRuleEvaluator anomalyAlertRuleEvaluator;

    public AlertRuleService(
            AutomationTreeService automationTreeService,
            ObjectManager objectManager,
            ExpressionEngine expressionEngine,
            EventService eventService,
            AutomationMetricsRecorder automationMetricsRecorder,
            NotificationDispatchService notificationDispatchService,
            AlarmShelfService alarmShelfService,
            AnomalyAlertRuleEvaluator anomalyAlertRuleEvaluator
    ) {
        this.automationTreeService = automationTreeService;
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
        this.eventService = eventService;
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.notificationDispatchService = notificationDispatchService;
        this.alarmShelfService = alarmShelfService;
        this.anomalyAlertRuleEvaluator = anomalyAlertRuleEvaluator;
    }

    @Transactional(readOnly = true)
    public List<AlertRule> list() {
        return automationTreeService.listAlertRules();
    }

    @Transactional(readOnly = true)
    public AlertRule get(String id) {
        return automationTreeService.getAlertRule(id);
    }

    @Transactional
    public AlertRule create(CreateAlertRuleRequest request) {
        validateRule(
                request.objectPath(),
                request.eventName(),
                request.conditionExpr(),
                request.anomalyModelId()
        );
        return automationTreeService.createAlertRule(
                request.name(),
                request.objectPath(),
                request.watchVariable(),
                request.conditionExpr(),
                request.eventName(),
                request.payloadVariable(),
                request.enabled(),
                request.edgeTrigger(),
                request.resolvedDelaySeconds(),
                request.resolvedSustainWhileTrue(),
                request.resolvedPriority(),
                request.resolvedAckRequired(),
                request.notificationWebhookUrl(),
                request.notificationEmailTarget(),
                request.anomalyModelId()
        );
    }

    @Transactional
    public AlertRule update(String id, UpdateAlertRuleRequest request) {
        if (request.objectPath() != null || request.eventName() != null || request.conditionExpr() != null) {
            AlertRule current = get(id);
            validateRule(
                    request.objectPath() != null ? request.objectPath() : current.objectPath(),
                    request.eventName() != null ? request.eventName() : current.eventName(),
                    request.conditionExpr() != null ? request.conditionExpr() : current.conditionExpr(),
                    request.anomalyModelId() != null ? request.anomalyModelId() : current.anomalyModelId()
            );
        }
        return automationTreeService.updateAlertRule(
                id,
                request.name(),
                request.objectPath(),
                request.watchVariable(),
                request.conditionExpr(),
                request.eventName(),
                request.payloadVariable(),
                request.enabled(),
                request.edgeTrigger(),
                request.delaySeconds(),
                request.sustainWhileTrue(),
                request.priority(),
                request.ackRequired(),
                request.rateLimitSeconds(),
                request.notificationWebhookUrl(),
                request.notificationEmailTarget(),
                request.anomalyModelId()
        );
    }

    @Transactional
    public void delete(String id) {
        automationTreeService.deleteAlertRule(id);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processVariableChange(String objectPath, String variableName) {
        List<AlertRule> rules = automationTreeService.findEnabledAlertRules(objectPath, variableName);
        if (rules.isEmpty()) {
            return;
        }

        PlatformObject node = objectManager.require(objectPath);
        for (AlertRule rule : rules) {
            automationMetricsRecorder.recordAlertEvaluation();
            if (!node.events().containsKey(rule.eventName())) {
                continue;
            }
            boolean conditionMet = usesAnomalyModel(rule)
                    ? anomalyAlertRuleEvaluator.evaluate(objectPath, rule.watchVariable(), rule.anomalyModelId())
                    : evaluateCondition(rule.conditionExpr(), node, rule.watchVariable());
            Double watchValue = readWatchValue(node, rule.watchVariable());
            Double previousWatchValue = automationTreeService.getAlertRuleLastWatchValue(rule.id());
            boolean shouldFire;

            if (rule.sustainWhileTrue() && rule.delaySeconds() > 0) {
                if (!conditionMet) {
                    automationTreeService.clearAlertRuleConditionTrueSince(rule.id());
                    automationTreeService.setAlertRuleLastConditionMet(rule.id(), false);
                    continue;
                }
                Instant conditionTrueSince = rule.conditionTrueSince();
                if (conditionTrueSince == null) {
                    automationTreeService.setAlertRuleConditionTrueSince(rule.id(), Instant.now());
                    automationTreeService.setAlertRuleLastConditionMet(rule.id(), true);
                    continue;
                }
                if (conditionTrueSince.plusSeconds(rule.delaySeconds()).isAfter(Instant.now())) {
                    automationTreeService.setAlertRuleLastConditionMet(rule.id(), true);
                    continue;
                }
                shouldFire = true;
            } else {
                shouldFire = conditionMet;
                if (rule.edgeTrigger()) {
                    boolean risingEdge = conditionMet && !Boolean.TRUE.equals(rule.lastConditionMet());
                    boolean watchIncreased = conditionMet
                            && watchValue != null
                            && previousWatchValue != null
                            && watchValue > previousWatchValue;
                    shouldFire = risingEdge || watchIncreased;
                }
            }

            if (shouldFire) {
                if (rule.rateLimitSeconds() > 0) {
                    if (rule.lastFiredAt() != null
                            && rule.lastFiredAt().plusSeconds(rule.rateLimitSeconds()).isAfter(Instant.now())) {
                        continue;
                    }
                    automationTreeService.setAlertRuleLastFiredAt(rule.id(), Instant.now());
                }
                DataRecord payload = resolvePayload(node, rule.payloadVariable());
                eventService.fireAutomation(objectPath, rule.eventName(), payload);
                automationMetricsRecorder.recordAlertFire();
                dispatchNotifications(rule, objectPath);
                if (rule.sustainWhileTrue() && rule.delaySeconds() > 0) {
                    automationTreeService.clearAlertRuleConditionTrueSince(rule.id());
                }
            }

            automationTreeService.setAlertRuleLastConditionMet(rule.id(), conditionMet);
            if (watchValue != null) {
                automationTreeService.setAlertRuleLastWatchValue(rule.id(), watchValue);
            }
        }
    }

    @Transactional
    public void ensureDemoRules() {
        automationTreeService.ensureDemoAlertRule();
    }

    private void dispatchNotifications(AlertRule rule, String objectPath) {
        if (alarmShelfService.isShelved(objectPath, rule.eventName())) {
            return;
        }
        if (!rule.hasNotificationChannel()) {
            return;
        }
        var context = notificationDispatchService.baseContext(
                "alert-rule",
                rule.id(),
                objectPath,
                rule.eventName()
        );
        try {
            if (rule.notificationWebhookUrl() != null && !rule.notificationWebhookUrl().isBlank()) {
                notificationDispatchService.sendWebhook(rule.notificationWebhookUrl(), context);
            }
            if (rule.notificationEmailTarget() != null && !rule.notificationEmailTarget().isBlank()) {
                notificationDispatchService.sendEmail(rule.notificationEmailTarget(), context);
            }
        } catch (Exception ex) {
            log.warn("Alert rule {} notification failed: {}", rule.id(), ex.getMessage());
        }
    }

    private DataRecord resolvePayload(PlatformObject node, String payloadVariable) {
        if (payloadVariable == null || payloadVariable.isBlank()) {
            return null;
        }
        return node.getVariable(payloadVariable)
                .flatMap(v -> v.value())
                .orElse(null);
    }

    private boolean evaluateCondition(String expression, PlatformObject node, String watchVariable) {
        try {
            Object result = expressionEngine.evaluateAlertCondition(expression, node, watchVariable);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (ExpressionException e) {
            return false;
        }
    }

    private static Double readWatchValue(PlatformObject node, String watchVariable) {
        if (watchVariable == null || watchVariable.isBlank()) {
            return null;
        }
        return node.getVariable(watchVariable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.doubleValue();
                    }
                    if (value instanceof Boolean bool) {
                        return bool ? 1.0 : 0.0;
                    }
                    return null;
                })
                .orElse(null);
    }

    private static boolean usesAnomalyModel(AlertRule rule) {
        return rule.anomalyModelId() != null && !rule.anomalyModelId().isBlank();
    }

    private void validateRule(String objectPath, String eventName, String conditionExpr, String anomalyModelId) {
        PlatformObject node = objectManager.require(objectPath);
        if (!node.events().containsKey(eventName)) {
            throw new IllegalArgumentException("Unknown event on object: " + eventName);
        }
        if (anomalyModelId == null || anomalyModelId.isBlank()) {
            expressionEngine.compile(conditionExpr);
        }
    }

    public record CreateAlertRuleRequest(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            boolean enabled,
            boolean edgeTrigger,
            Integer delaySeconds,
            Boolean sustainWhileTrue,
            String priority,
            Boolean ackRequired,
            String notificationWebhookUrl,
            String notificationEmailTarget,
            String anomalyModelId
    ) {
        public int resolvedDelaySeconds() {
            return delaySeconds != null ? delaySeconds : 0;
        }

        public boolean resolvedSustainWhileTrue() {
            return sustainWhileTrue != null && sustainWhileTrue;
        }

        public String resolvedPriority() {
            return priority != null && !priority.isBlank() ? priority : "HIGH";
        }

        public boolean resolvedAckRequired() {
            return ackRequired != null && ackRequired;
        }
    }

    public record UpdateAlertRuleRequest(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            Boolean enabled,
            Boolean edgeTrigger,
            Integer delaySeconds,
            Boolean sustainWhileTrue,
            String priority,
            Boolean ackRequired,
            Integer rateLimitSeconds,
            String notificationWebhookUrl,
            String notificationEmailTarget,
            String anomalyModelId
    ) {
    }
}
