package com.ispf.server.alert;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        validateTargetAndExpressions(
                request.objectPath(),
                request.conditionExpr(),
                request.anomalyModelId(),
                request.deactivateExpr()
        );
        AlertRule created = automationTreeService.createAlertRule(
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
                request.anomalyModelId(),
                request.deactivateExpr(),
                request.resolvedDeactivateDelaySeconds(),
                request.resolvedPollIntervalMs(),
                request.triggerMessage(),
                request.clearEventName()
        );
        automationTreeService.ensureAlertRuleEvents(
                created.id(),
                created.eventName(),
                created.clearEventName()
        );
        return get(created.id());
    }

    @Transactional
    public AlertRule update(String id, UpdateAlertRuleRequest request) {
        AlertRule current = get(id);
        if (request.objectPath() != null || request.conditionExpr() != null
                || request.deactivateExpr() != null || request.anomalyModelId() != null) {
            validateTargetAndExpressions(
                    request.objectPath() != null ? request.objectPath() : current.objectPath(),
                    request.conditionExpr() != null ? request.conditionExpr() : current.conditionExpr(),
                    request.anomalyModelId() != null ? request.anomalyModelId() : current.anomalyModelId(),
                    request.deactivateExpr() != null ? request.deactivateExpr() : current.deactivateExpr()
            );
        }
        AlertRule updated = automationTreeService.updateAlertRule(
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
                request.anomalyModelId(),
                request.deactivateExpr(),
                request.deactivateDelaySeconds(),
                request.pollIntervalMs(),
                request.triggerMessage(),
                request.clearEventName()
        );
        automationTreeService.ensureAlertRuleEvents(
                updated.id(),
                updated.eventName(),
                updated.clearEventName()
        );
        return get(updated.id());
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
        for (AlertRule rule : rules) {
            evaluateRule(rule);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void evaluateRule(AlertRule rule) {
        rule = automationTreeService.getAlertRule(rule.id());
        if (!rule.enabled()) {
            return;
        }
        PlatformObject target = objectManager.require(rule.objectPath());
        automationMetricsRecorder.recordAlertEvaluation();

        boolean conditionMet = usesAnomalyModel(rule)
                ? anomalyAlertRuleEvaluator.evaluate(rule.objectPath(), rule.watchVariable(), rule.anomalyModelId())
                : evaluateCondition(rule.conditionExpr(), target, rule.watchVariable());
        boolean deactivateMet = evaluateDeactivateMet(rule, target, conditionMet);
        boolean latched = Boolean.TRUE.equals(rule.latchedActive());

        if (rule.usesLatch() && latched) {
            if (tryClearLatched(rule, target, deactivateMet)) {
                automationTreeService.setAlertRuleLastConditionMet(rule.id(), conditionMet);
                updateWatchValue(rule, target);
            }
            return;
        }

        automationTreeService.ensureAlertRuleEvents(rule.id(), rule.eventName(), rule.clearEventName());
        PlatformObject alertNode = objectManager.require(rule.id());
        if (!alertNode.events().containsKey(rule.eventName())) {
            log.warn("Alert rule {} raise event {} missing on ALERT node after ensure", rule.id(), rule.eventName());
            return;
        }

        Double watchValue = readWatchValue(target, rule.watchVariable());
        Double previousWatchValue = automationTreeService.getAlertRuleLastWatchValue(rule.id());
        boolean shouldFire = resolveShouldFire(rule, conditionMet, watchValue, previousWatchValue);

        if (shouldFire) {
            if (rule.rateLimitSeconds() > 0
                    && rule.lastFiredAt() != null
                    && rule.lastFiredAt().plusSeconds(rule.rateLimitSeconds()).isAfter(Instant.now())) {
                automationTreeService.setAlertRuleLastConditionMet(rule.id(), conditionMet);
                updateWatchValue(rule, target);
                return;
            }
            if (rule.rateLimitSeconds() > 0) {
                automationTreeService.setAlertRuleLastFiredAt(rule.id(), Instant.now());
            }
            fireRaiseEvent(rule, target);
            if (rule.usesLatch()) {
                automationTreeService.setAlertRuleLatchedActive(rule.id(), true);
                automationTreeService.clearAlertRuleDeactivateTrueSince(rule.id());
            }
            if (rule.sustainWhileTrue() && rule.delaySeconds() > 0) {
                automationTreeService.clearAlertRuleConditionTrueSince(rule.id());
            }
        }

        automationTreeService.setAlertRuleLastConditionMet(rule.id(), conditionMet);
        updateWatchValue(rule, target);
    }

    private boolean tryClearLatched(AlertRule rule, PlatformObject target, boolean deactivateMet) {
        if (!deactivateMet) {
            automationTreeService.clearAlertRuleDeactivateTrueSince(rule.id());
            return true;
        }
        if (rule.deactivateDelaySeconds() > 0) {
            Instant since = rule.deactivateTrueSince();
            if (since == null) {
                automationTreeService.setAlertRuleDeactivateTrueSince(rule.id(), Instant.now());
                return true;
            }
            if (since.plusSeconds(rule.deactivateDelaySeconds()).isAfter(Instant.now())) {
                return true;
            }
        }
        fireClearEvent(rule, target);
        automationTreeService.setAlertRuleLatchedActive(rule.id(), false);
        automationTreeService.clearAlertRuleDeactivateTrueSince(rule.id());
        return true;
    }

    private boolean resolveShouldFire(
            AlertRule rule,
            boolean conditionMet,
            Double watchValue,
            Double previousWatchValue
    ) {
        if (rule.sustainWhileTrue() && rule.delaySeconds() > 0) {
            if (!conditionMet) {
                automationTreeService.clearAlertRuleConditionTrueSince(rule.id());
                return false;
            }
            Instant conditionTrueSince = rule.conditionTrueSince();
            if (conditionTrueSince == null) {
                automationTreeService.setAlertRuleConditionTrueSince(rule.id(), Instant.now());
                return false;
            }
            if (conditionTrueSince.plusSeconds(rule.delaySeconds()).isAfter(Instant.now())) {
                return false;
            }
            return true;
        }
        if (!conditionMet) {
            return false;
        }
        if (!rule.edgeTrigger()) {
            return true;
        }
        boolean risingEdge = !Boolean.TRUE.equals(rule.lastConditionMet());
        boolean watchIncreased = watchValue != null
                && previousWatchValue != null
                && watchValue > previousWatchValue;
        return risingEdge || watchIncreased;
    }

    private void fireRaiseEvent(AlertRule rule, PlatformObject target) {
        DataRecord payload = buildFirePayload(rule, target);
        eventService.fireAutomation(rule.id(), rule.eventName(), payload);
        automationMetricsRecorder.recordAlertFire();
        dispatchNotifications(rule, rule.id(), rule.eventName());
    }

    private void fireClearEvent(AlertRule rule, PlatformObject target) {
        String clearEvent = rule.clearEventName();
        if (clearEvent == null || clearEvent.isBlank()) {
            return;
        }
        automationTreeService.ensureAlertRuleEvents(rule.id(), rule.eventName(), clearEvent);
        PlatformObject alertNode = objectManager.require(rule.id());
        if (!alertNode.events().containsKey(clearEvent)) {
            log.warn("Alert rule {} clear event {} not defined on ALERT node {}", rule.id(), clearEvent, rule.id());
            return;
        }
        if (alarmShelfService.isShelved(rule.id(), clearEvent)) {
            return;
        }
        DataRecord payload = buildFirePayload(rule, target);
        eventService.fireAutomation(rule.id(), clearEvent, payload);
        automationMetricsRecorder.recordAlertFire();
        dispatchNotifications(rule, rule.id(), clearEvent);
    }

    private DataRecord buildFirePayload(AlertRule rule, PlatformObject target) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("targetObjectPath", rule.objectPath());
        row.put("watchVariable", rule.watchVariable());
        row.put("alertRulePath", rule.id());
        if (rule.priority() != null && !rule.priority().isBlank()) {
            row.put("alertPriority", rule.priority());
        }
        DataRecord fromVariable = resolvePayload(target, rule.payloadVariable());
        if (fromVariable != null && fromVariable.rowCount() > 0) {
            Map<String, Object> source = fromVariable.firstRow();
            Object value = source.get("value");
            if (value != null) {
                row.put("value", String.valueOf(value));
            }
            Object message = source.get("message");
            if (message != null) {
                row.put("message", String.valueOf(message));
            }
            Object priority = source.get("alertPriority");
            if (priority != null) {
                row.put("alertPriority", String.valueOf(priority));
            }
        }
        return DataRecord.single(AutomationTreeService.ALERT_FIRE_PAYLOAD_SCHEMA, row);
    }

    private boolean evaluateDeactivateMet(AlertRule rule, PlatformObject node, boolean conditionMet) {
        String expr = rule.deactivateExpr();
        if (expr != null && !expr.isBlank()) {
            return evaluateCondition(expr, node, rule.watchVariable());
        }
        return !conditionMet;
    }

    private void updateWatchValue(AlertRule rule, PlatformObject node) {
        Double watchValue = readWatchValue(node, rule.watchVariable());
        if (watchValue != null) {
            automationTreeService.setAlertRuleLastWatchValue(rule.id(), watchValue);
        }
    }

    @Transactional
    public void ensureDemoRules() {
        automationTreeService.ensureDemoAlertRule();
    }

    private void dispatchNotifications(AlertRule rule, String objectPath, String eventName) {
        if (alarmShelfService.isShelved(objectPath, eventName)) {
            return;
        }
        if (!rule.hasNotificationChannel()) {
            return;
        }
        Map<String, Object> context = new HashMap<>(notificationDispatchService.baseContext(
                "alert-rule",
                rule.id(),
                objectPath,
                eventName
        ));
        String message = resolveTriggerMessage(rule, objectPath);
        if (message != null) {
            context.put("triggerMessage", message);
        }
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

    private String resolveTriggerMessage(AlertRule rule, String notificationObjectPath) {
        String template = rule.triggerMessage();
        if (template == null || template.isBlank()) {
            return null;
        }
        try {
            // Evaluate message template against the watched target object.
            PlatformObject node = objectManager.require(rule.objectPath());
            Object result = expressionEngine.evaluateAlertCondition(template, node, rule.watchVariable());
            return result != null ? String.valueOf(result) : null;
        } catch (ExpressionException ex) {
            return template;
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

    /** Validate target object exists and CEL expressions compile. Events live on the ALERT node. */
    private void validateTargetAndExpressions(
            String targetObjectPath,
            String conditionExpr,
            String anomalyModelId,
            String deactivateExpr
    ) {
        objectManager.require(targetObjectPath);
        if (anomalyModelId == null || anomalyModelId.isBlank()) {
            if (conditionExpr == null || conditionExpr.isBlank()) {
                throw new IllegalArgumentException("conditionExpr is required when anomalyModelId is empty");
            }
            expressionEngine.compile(conditionExpr);
        }
        if (deactivateExpr != null && !deactivateExpr.isBlank()) {
            expressionEngine.compile(deactivateExpr);
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
            String anomalyModelId,
            String deactivateExpr,
            Integer deactivateDelaySeconds,
            Integer pollIntervalMs,
            String triggerMessage,
            String clearEventName
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

        public int resolvedDeactivateDelaySeconds() {
            return deactivateDelaySeconds != null ? deactivateDelaySeconds : 0;
        }

        public int resolvedPollIntervalMs() {
            return pollIntervalMs != null ? pollIntervalMs : 0;
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
            String anomalyModelId,
            String deactivateExpr,
            Integer deactivateDelaySeconds,
            Integer pollIntervalMs,
            String triggerMessage,
            String clearEventName
    ) {
    }
}
