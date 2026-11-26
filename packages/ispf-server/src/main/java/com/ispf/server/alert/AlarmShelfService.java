package com.ispf.server.alert;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.AlarmShelfRepository;
import com.ispf.server.persistence.entity.AlarmShelfEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AlarmShelfService {

    private final AlarmShelfRepository repository;
    private final ObjectManager objectManager;

    public AlarmShelfService(AlarmShelfRepository repository, ObjectManager objectManager) {
        this.repository = repository;
        this.objectManager = objectManager;
    }

    @Transactional(readOnly = true)
    public List<AlarmShelf> listActive() {
        return repository.findByActiveTrueOrderByShelvedAtDesc().stream()
                .filter(this::stillEffective)
                .map(this::toRecord)
                .toList();
    }

    /** Hot path from alert automation — read-only; expiry handled by {@link #expireStaleScheduled()}. */
    @Transactional(readOnly = true)
    public boolean isShelved(String objectPath, String eventName) {
        return repository.findActiveShelf(objectPath, eventName, Instant.now()).isPresent();
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleScheduled() {
        expireStale();
    }

    @Transactional
    public AlarmShelf shelve(ShelveAlarmRequest request) {
        if (request.durationMinutes() != null && request.durationMinutes() <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        validateTarget(request);
        String objectPath = request.objectPath().trim();
        String eventName = request.eventName().trim();
        expireStale();
        repository.findActiveShelf(objectPath, eventName, Instant.now())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    repository.save(existing);
                });

        AlarmShelfEntity entity = new AlarmShelfEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setObjectPath(objectPath);
        entity.setEventName(eventName);
        entity.setAlertRulePath(blankToNull(request.alertRulePath()));
        entity.setShelvedBy(currentUsername());
        entity.setShelvedAt(Instant.now());
        entity.setExpiresAt(resolveExpiresAt(request.durationMinutes()));
        entity.setComment(blankToNull(request.comment()));
        entity.setActive(true);
        return toRecord(repository.save(entity));
    }

    @Transactional
    public void unshelve(String id) {
        AlarmShelfEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown alarm shelf: " + id));
        entity.setActive(false);
        repository.save(entity);
    }

    @Transactional
    public void expireStale() {
        repository.deactivateExpired(Instant.now());
    }

    private void validateTarget(ShelveAlarmRequest request) {
        String objectPath = request.objectPath();
        if (objectPath == null || objectPath.isBlank()) {
            throw new IllegalArgumentException("objectPath is required");
        }
        String eventName = request.eventName();
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        String path = objectPath.trim();
        String event = eventName.trim();
        PlatformObject object = objectManager.tree().findByPath(path)
                .orElseThrow(() -> new IllegalArgumentException("Unknown object: " + path));
        if (object.events().get(event) == null) {
            throw new IllegalArgumentException("Unknown event: " + event);
        }
        String rulePath = blankToNull(request.alertRulePath());
        if (rulePath == null) {
            return;
        }
        boolean rule = objectManager.tree().findByPath(rulePath)
                .filter(node -> node.type() == ObjectType.ALERT)
                .isPresent();
        if (!rule) {
            throw new IllegalArgumentException("Unknown alert rule: " + rulePath);
        }
    }

    private boolean stillEffective(AlarmShelfEntity entity) {
        return entity.isActive()
                && (entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(Instant.now()));
    }

    private static Instant resolveExpiresAt(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            return null;
        }
        return Instant.now().plusSeconds(durationMinutes.longValue() * 60L);
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AlarmShelf toRecord(AlarmShelfEntity entity) {
        return new AlarmShelf(
                entity.getId(),
                entity.getObjectPath(),
                entity.getEventName(),
                entity.getAlertRulePath(),
                entity.getShelvedBy(),
                entity.getShelvedAt(),
                entity.getExpiresAt(),
                entity.getComment(),
                entity.isActive()
        );
    }

    public record ShelveAlarmRequest(
            String objectPath,
            String eventName,
            String alertRulePath,
            Integer durationMinutes,
            String comment
    ) {
    }
}
