package com.ispf.server.alert;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory shelving approval queue — BL-158 stub (persistence is follow-up work).
 */
@Service
public class AlarmShelfApprovalService {

    private final Map<String, PendingShelfRequest> pendingById = new ConcurrentHashMap<>();

    public PendingShelfRequest submit(AlarmShelfService.ShelveAlarmRequest request, String requestedBy) {
        String id = UUID.randomUUID().toString();
        PendingShelfRequest pending = new PendingShelfRequest(
                id,
                request.objectPath(),
                request.eventName(),
                request.alertRulePath(),
                request.durationMinutes(),
                request.comment(),
                requestedBy,
                Instant.now()
        );
        pendingById.put(id, pending);
        return pending;
    }

    public List<PendingShelfRequest> listPending() {
        return pendingById.values().stream()
                .sorted((a, b) -> b.requestedAt().compareTo(a.requestedAt()))
                .toList();
    }

    public Optional<PendingShelfRequest> findPending(String id) {
        return Optional.ofNullable(pendingById.get(id));
    }

    public Optional<PendingShelfRequest> removePending(String id) {
        return Optional.ofNullable(pendingById.remove(id));
    }

    public void reject(String id) {
        if (removePending(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown shelf request: " + id);
        }
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
