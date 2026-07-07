package com.ispf.server.audit;

import tools.jackson.databind.ObjectMapper;
import com.ispf.server.persistence.AuditEventRepository;
import com.ispf.server.persistence.entity.AuditEventEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable security audit log — BL-156.
 */
@Service
public class AuditEventService {

    public static final String CATEGORY_AUTH = "auth";
    public static final String CATEGORY_MFA = "mfa";
    public static final String CATEGORY_ACL = "acl";

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditEventService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void log(String category, String action, String actor, String targetType, String targetId, Map<String, ?> details) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCategory(category);
        entity.setAction(action);
        entity.setActor(actor);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetailsJson(details == null || details.isEmpty() ? null : writeJson(details));
        entity.setOccurredAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> listRecent(String category, int limit) {
        int capped = Math.min(Math.max(limit, 1), 1000);
        PageRequest page = PageRequest.of(0, capped);
        List<AuditEventEntity> rows = category == null || category.isBlank()
                ? repository.findAllByOrderByOccurredAtDesc(page)
                : repository.findByCategoryOrderByOccurredAtDesc(category.trim(), page);
        return rows.stream().map(this::toRecord).toList();
    }

    public void logLoginSuccess(String username) {
        log(CATEGORY_AUTH, "login.success", username, "user", username, Map.of());
    }

    public void logLoginFailure(String username) {
        log(CATEGORY_AUTH, "login.failure", username, "user", username, Map.of());
    }

    public void logMfaEnrollmentStarted(String username) {
        log(CATEGORY_MFA, "enrollment.started", username, "user", username, Map.of());
    }

    public void logMfaEnrollmentVerified(String username) {
        log(CATEGORY_MFA, "enrollment.verified", username, "user", username, Map.of());
    }

    public void logMfaEnrollmentCancelled(String username) {
        log(CATEGORY_MFA, "enrollment.cancelled", username, "user", username, Map.of());
    }

    public void logVariableAclChange(
            String actor,
            String objectPath,
            String variableName,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (readRoles != null) {
            details.put("readRoles", readRoles);
        }
        if (writeRoles != null) {
            details.put("writeRoles", writeRoles);
        }
        log(
                CATEGORY_ACL,
                "variable.acl.updated",
                actor,
                "variable",
                objectPath + "#" + variableName,
                details
        );
    }

    private AuditEvent toRecord(AuditEventEntity entity) {
        return new AuditEvent(
                entity.getId(),
                entity.getCategory(),
                entity.getAction(),
                entity.getActor(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getDetailsJson(),
                entity.getOccurredAt()
        );
    }

    private String writeJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception ex) {
            return "{\"error\":\"serialization-failed\"}";
        }
    }

    public record AuditEvent(
            String id,
            String category,
            String action,
            String actor,
            String targetType,
            String targetId,
            String detailsJson,
            Instant occurredAt
    ) {
    }
}
