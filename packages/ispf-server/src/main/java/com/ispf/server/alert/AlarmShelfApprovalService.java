package com.ispf.server.alert;

import com.ispf.server.persistence.AlarmShelfRequestRepository;
import com.ispf.server.persistence.entity.AlarmShelfRequestEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted shelving approval queue — BL-158.
 */
@Service
public class AlarmShelfApprovalService {

    private final AlarmShelfRequestRepository repository;

    public AlarmShelfApprovalService(AlarmShelfRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PendingShelfRequest submit(AlarmShelfService.ShelveAlarmRequest request, String requestedBy) {
        AlarmShelfRequestEntity entity = new AlarmShelfRequestEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setObjectPath(request.objectPath());
        entity.setEventName(request.eventName());
        entity.setAlertRulePath(blankToNull(request.alertRulePath()));
        entity.setDurationMinutes(request.durationMinutes());
        entity.setComment(blankToNull(request.comment()));
        entity.setRequestedBy(requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy.trim());
        entity.setRequestedAt(Instant.now());
        return toRecord(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<PendingShelfRequest> listPending() {
        return repository.findAllByOrderByRequestedAtDesc().stream()
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PendingShelfRequest> findPending(String id) {
        return repository.findById(id).map(this::toRecord);
    }

    @Transactional
    public Optional<PendingShelfRequest> removePending(String id) {
        Optional<AlarmShelfRequestEntity> existing = repository.findById(id);
        existing.ifPresent(repository::delete);
        return existing.map(this::toRecord);
    }

    @Transactional
    public void reject(String id) {
        if (removePending(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown shelf request: " + id);
        }
    }

    private PendingShelfRequest toRecord(AlarmShelfRequestEntity entity) {
        return new PendingShelfRequest(
                entity.getId(),
                entity.getObjectPath(),
                entity.getEventName(),
                entity.getAlertRulePath(),
                entity.getDurationMinutes(),
                entity.getComment(),
                entity.getRequestedBy(),
                entity.getRequestedAt()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PendingShelfRequest(
            String id,
            String objectPath,
            String eventName,
            String alertRulePath,
            Integer durationMinutes,
            String comment,
            String requestedBy,
            Instant requestedAt
    ) {
        public AlarmShelfService.ShelveAlarmRequest toShelveRequest() {
            return new AlarmShelfService.ShelveAlarmRequest(
                    objectPath,
                    eventName,
                    alertRulePath,
                    durationMinutes,
                    comment
            );
        }
    }
}
