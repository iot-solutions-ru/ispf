package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.event.EventService;
import com.ispf.server.persistence.AlertRuleRepository;
import com.ispf.server.persistence.entity.AlertRuleEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AlertRuleService {

    private final AlertRuleRepository repository;
    private final ObjectManager objectManager;
    private final ExpressionEngine expressionEngine;
    private final EventService eventService;

    public AlertRuleService(
            AlertRuleRepository repository,
            ObjectManager objectManager,
            ExpressionEngine expressionEngine,
            EventService eventService
    ) {
        this.repository = repository;
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<AlertRule> list() {
        return repository.findAll().stream().map(this::toModel).toList();
    }

    @Transactional(readOnly = true)
    public AlertRule get(String id) {
        return toModel(repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id)));
    }

    @Transactional
    public AlertRule create(CreateAlertRuleRequest request) {
        validateRule(request.objectPath(), request.eventName(), request.conditionExpr());
        Instant now = Instant.now();
        AlertRuleEntity entity = new AlertRuleEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(request.name());
        entity.setObjectPath(request.objectPath());
        entity.setWatchVariable(request.watchVariable());
        entity.setConditionExpr(request.conditionExpr());
        entity.setEventName(request.eventName());
        entity.setPayloadVariable(request.payloadVariable());
        entity.setEnabled(request.enabled());
        entity.setEdgeTrigger(request.edgeTrigger());
        entity.setLastConditionMet(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toModel(repository.save(entity));
    }

    @Transactional
    public AlertRule update(String id, UpdateAlertRuleRequest request) {
        AlertRuleEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.objectPath() != null) {
            entity.setObjectPath(request.objectPath());
        }
        if (request.watchVariable() != null) {
            entity.setWatchVariable(request.watchVariable());
        }
        if (request.conditionExpr() != null) {
            entity.setConditionExpr(request.conditionExpr());
        }
        if (request.eventName() != null) {
            entity.setEventName(request.eventName());
        }
        if (request.payloadVariable() != null) {
            entity.setPayloadVariable(request.payloadVariable().isBlank() ? null : request.payloadVariable());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.edgeTrigger() != null) {
            entity.setEdgeTrigger(request.edgeTrigger());
        }
        validateRule(entity.getObjectPath(), entity.getEventName(), entity.getConditionExpr());
        entity.setUpdatedAt(Instant.now());
        return toModel(repository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Alert rule not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public void processVariableChange(String objectPath, String variableName) {
        List<AlertRuleEntity> rules = repository.findByObjectPathAndWatchVariableAndEnabledTrue(
                objectPath,
                variableName
        );
        if (rules.isEmpty()) {
            return;
        }

        PlatformObject node = objectManager.require(objectPath);
        for (AlertRuleEntity rule : rules) {
            if (!node.events().containsKey(rule.getEventName())) {
                continue;
            }
            boolean conditionMet = evaluateCondition(rule.getConditionExpr(), node);
            boolean shouldFire = conditionMet;
            if (rule.isEdgeTrigger()) {
                shouldFire = conditionMet && !Boolean.TRUE.equals(rule.getLastConditionMet());
            }
            rule.setLastConditionMet(conditionMet);
            rule.setUpdatedAt(Instant.now());
            repository.save(rule);
            if (shouldFire) {
                DataRecord payload = resolvePayload(node, rule.getPayloadVariable());
                eventService.fire(objectPath, rule.getEventName(), payload);
            }
        }
    }

    @Transactional
    public void ensureDemoRules() {
        if (repository.count() > 0) {
            return;
        }
        create(new CreateAlertRuleRequest(
                "Temperature threshold exceeded",
                "root.platform.devices.demo-sensor-01",
                "alarmActive",
                "self.alarmActive[\"value\"] == true",
                "thresholdExceeded",
                "temperature",
                true,
                true
        ));
    }

    private DataRecord resolvePayload(PlatformObject node, String payloadVariable) {
        if (payloadVariable == null || payloadVariable.isBlank()) {
            return null;
        }
        return node.getVariable(payloadVariable)
                .flatMap(v -> v.value())
                .orElse(null);
    }

    private boolean evaluateCondition(String expression, PlatformObject node) {
        try {
            Object result = expressionEngine.evaluate(expression, node);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (ExpressionException e) {
            return false;
        }
    }

    private void validateRule(String objectPath, String eventName, String conditionExpr) {
        PlatformObject node = objectManager.require(objectPath);
        if (!node.events().containsKey(eventName)) {
            throw new IllegalArgumentException("Unknown event on object: " + eventName);
        }
        expressionEngine.compile(conditionExpr);
    }

    private AlertRule toModel(AlertRuleEntity entity) {
        return new AlertRule(
                entity.getId(),
                entity.getName(),
                entity.getObjectPath(),
                entity.getWatchVariable(),
                entity.getConditionExpr(),
                entity.getEventName(),
                entity.getPayloadVariable(),
                entity.isEnabled(),
                entity.isEdgeTrigger(),
                entity.getLastConditionMet(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record CreateAlertRuleRequest(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            boolean enabled,
            boolean edgeTrigger
    ) {
    }

    public record UpdateAlertRuleRequest(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            Boolean enabled,
            Boolean edgeTrigger
    ) {
    }
}
