package com.ispf.server.alert;

import com.ispf.server.persistence.AlarmShelfRepository;
import com.ispf.server.persistence.entity.AlarmShelfEntity;
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

    public AlarmShelfService(AlarmShelfRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AlarmShelf> listActive() {
        expireStale();
        return repository.findByActiveTrueOrderByShelvedAtDesc().stream()
                .filter(this::stillEffective)
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isShelved(String objectPath, String eventName) {
        expireStale();
        return repository.findActiveShelf(objectPath, eventName, Instant.now()).isPresent();
    }

    @Transactional
    public AlarmShelf shelve(ShelveAlarmRequest request) {
        expireStale();
        repository.findActiveShelf(request.objectPath(), request.eventName(), Instant.now())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    repository.save(existing);
                });

        AlarmShelfEntity entity = new AlarmShelfEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setObjectPath(request.objectPath());
        entity.setEventName(request.eventName());
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
