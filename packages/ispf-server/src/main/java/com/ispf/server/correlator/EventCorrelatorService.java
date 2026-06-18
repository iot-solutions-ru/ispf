package com.ispf.server.correlator;

import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.persistence.CorrelatorHitRepository;
import com.ispf.server.persistence.EventCorrelatorRepository;
import com.ispf.server.persistence.entity.CorrelatorHitEntity;
import com.ispf.server.persistence.entity.EventCorrelatorEntity;
import com.ispf.server.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class EventCorrelatorService {

    private static final Logger log = LoggerFactory.getLogger(EventCorrelatorService.class);

    private final EventCorrelatorRepository correlatorRepository;
    private final CorrelatorHitRepository hitRepository;
    private final WorkflowService workflowService;

    public EventCorrelatorService(
            EventCorrelatorRepository correlatorRepository,
            CorrelatorHitRepository hitRepository,
            @Lazy WorkflowService workflowService
    ) {
        this.correlatorRepository = correlatorRepository;
        this.hitRepository = hitRepository;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public List<EventCorrelator> list() {
        return correlatorRepository.findAll().stream().map(this::toModel).toList();
    }

    @Transactional(readOnly = true)
    public EventCorrelator get(String id) {
        return toModel(correlatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Correlator not found: " + id)));
    }

    @Transactional
    public EventCorrelator create(CreateCorrelatorRequest request) {
        validateRequest(request.actionType(), request.actionTarget(), request.minOccurrences(), request.windowSeconds());
        Instant now = Instant.now();
        EventCorrelatorEntity entity = new EventCorrelatorEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(request.name());
        entity.setObjectPath(blankToNull(request.objectPath()));
        entity.setEventName(request.eventName());
        entity.setWindowSeconds(request.windowSeconds());
        entity.setMinOccurrences(request.minOccurrences());
        entity.setCooldownSeconds(request.cooldownSeconds());
        entity.setActionType(request.actionType().name());
        entity.setActionTarget(request.actionTarget());
        entity.setEnabled(request.enabled());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toModel(correlatorRepository.save(entity));
    }

    @Transactional
    public EventCorrelator update(String id, UpdateCorrelatorRequest request) {
        EventCorrelatorEntity entity = correlatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Correlator not found: " + id));
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.objectPath() != null) {
            entity.setObjectPath(blankToNull(request.objectPath()));
        }
        if (request.eventName() != null) {
            entity.setEventName(request.eventName());
        }
        if (request.windowSeconds() != null) {
            entity.setWindowSeconds(request.windowSeconds());
        }
        if (request.minOccurrences() != null) {
            entity.setMinOccurrences(request.minOccurrences());
        }
        if (request.cooldownSeconds() != null) {
            entity.setCooldownSeconds(request.cooldownSeconds());
        }
        if (request.actionType() != null) {
            entity.setActionType(request.actionType().name());
        }
        if (request.actionTarget() != null) {
            entity.setActionTarget(request.actionTarget());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        validateRequest(
                CorrelatorActionType.valueOf(entity.getActionType()),
                entity.getActionTarget(),
                entity.getMinOccurrences(),
                entity.getWindowSeconds()
        );
        entity.setUpdatedAt(Instant.now());
        return toModel(correlatorRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        if (!correlatorRepository.existsById(id)) {
            throw new IllegalArgumentException("Correlator not found: " + id);
        }
        hitRepository.deleteByCorrelatorId(id);
        correlatorRepository.deleteById(id);
    }

    @Transactional
    public void processEventFired(String objectPath, String eventName) {
        List<EventCorrelatorEntity> correlators = correlatorRepository.findByEventNameAndEnabledTrue(eventName);
        Instant now = Instant.now();
        for (EventCorrelatorEntity correlator : correlators) {
            if (!matchesObject(correlator.getObjectPath(), objectPath)) {
                continue;
            }
            if (isInCooldown(correlator, now)) {
                continue;
            }
            recordHit(correlator.getId(), objectPath, now);
            if (!thresholdMet(correlator, objectPath, now)) {
                continue;
            }
            executeAction(correlator, objectPath);
            correlator.setLastTriggeredAt(now);
            correlator.setUpdatedAt(now);
            correlatorRepository.save(correlator);
            hitRepository.deleteByCorrelatorId(correlator.getId());
        }
        hitRepository.deleteOlderThan(now.minus(1, ChronoUnit.HOURS));
    }

    @Transactional
    public void ensureDemoCorrelators() {
        if (correlatorRepository.count() > 0) {
            return;
        }
        create(new CreateCorrelatorRequest(
                "Alarm handler on threshold event",
                "root.platform.devices.demo-sensor-01",
                "thresholdExceeded",
                0,
                1,
                120,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                true
        ));
    }

    private void executeAction(EventCorrelatorEntity correlator, String objectPath) {
        CorrelatorActionType actionType = CorrelatorActionType.valueOf(correlator.getActionType());
        try {
            switch (actionType) {
                case RUN_WORKFLOW -> workflowService.runWorkflow(correlator.getActionTarget(), objectPath);
            }
        } catch (WorkflowException e) {
            log.warn("Correlator {} action failed: {}", correlator.getId(), e.getMessage());
        }
    }

    private boolean thresholdMet(EventCorrelatorEntity correlator, String objectPath, Instant now) {
        if (correlator.getWindowSeconds() <= 0) {
            return correlator.getMinOccurrences() <= 1
                    || hitRepository.countByCorrelatorIdAndObjectPathAndOccurredAtAfter(
                    correlator.getId(), objectPath, now.minusSeconds(1)) >= correlator.getMinOccurrences();
        }
        Instant since = now.minusSeconds(correlator.getWindowSeconds());
        long hits = hitRepository.countByCorrelatorIdAndObjectPathAndOccurredAtAfter(
                correlator.getId(),
                objectPath,
                since
        );
        return hits >= correlator.getMinOccurrences();
    }

    private void recordHit(String correlatorId, String objectPath, Instant now) {
        CorrelatorHitEntity hit = new CorrelatorHitEntity();
        hit.setCorrelatorId(correlatorId);
        hit.setObjectPath(objectPath);
        hit.setOccurredAt(now);
        hitRepository.save(hit);
    }

    private static boolean isInCooldown(EventCorrelatorEntity correlator, Instant now) {
        if (correlator.getCooldownSeconds() <= 0 || correlator.getLastTriggeredAt() == null) {
            return false;
        }
        return correlator.getLastTriggeredAt()
                .plusSeconds(correlator.getCooldownSeconds())
                .isAfter(now);
    }

    private static boolean matchesObject(String filterPath, String eventPath) {
        return filterPath == null || filterPath.isBlank() || filterPath.equals(eventPath);
    }

    private static void validateRequest(
            CorrelatorActionType actionType,
            String actionTarget,
            int minOccurrences,
            int windowSeconds
    ) {
        if (actionTarget == null || actionTarget.isBlank()) {
            throw new IllegalArgumentException("actionTarget is required");
        }
        if (minOccurrences < 1) {
            throw new IllegalArgumentException("minOccurrences must be >= 1");
        }
        if (windowSeconds < 0) {
            throw new IllegalArgumentException("windowSeconds must be >= 0");
        }
        if (actionType != CorrelatorActionType.RUN_WORKFLOW) {
            throw new IllegalArgumentException("Unsupported action type: " + actionType);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private EventCorrelator toModel(EventCorrelatorEntity entity) {
        return new EventCorrelator(
                entity.getId(),
                entity.getName(),
                entity.getObjectPath(),
                entity.getEventName(),
                entity.getWindowSeconds(),
                entity.getMinOccurrences(),
                entity.getCooldownSeconds(),
                CorrelatorActionType.valueOf(entity.getActionType()),
                entity.getActionTarget(),
                entity.isEnabled(),
                entity.getLastTriggeredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record CreateCorrelatorRequest(
            String name,
            String objectPath,
            String eventName,
            int windowSeconds,
            int minOccurrences,
            int cooldownSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            boolean enabled
    ) {
    }

    public record UpdateCorrelatorRequest(
            String name,
            String objectPath,
            String eventName,
            Integer windowSeconds,
            Integer minOccurrences,
            Integer cooldownSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            Boolean enabled
    ) {
    }
}
