package com.ispf.server.ai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class AiToolAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiToolAuditService.class);

    private final AiToolAuditStore store;

    public AiToolAuditService(AiToolAuditStore store) {
        this.store = store;
    }

    public long record(
            String toolName,
            String appId,
            String actor,
            String requestBody,
            String status,
            String providerId,
            String modelId,
            String contextPackVersion,
            List<String> errors
    ) {
        return record(
                toolName,
                appId,
                actor,
                requestBody,
                status,
                providerId,
                modelId,
                contextPackVersion,
                errors,
                AgentAuditMetrics.empty()
        );
    }

    /**
     * Values are clipped to the ai_tool_audit column sizes. A model can emit a garbage tool name
     * (a whole JSON tail); that must not fail the agent turn.
     */
    public long record(
            String toolName,
            String appId,
            String actor,
            String requestBody,
            String status,
            String providerId,
            String modelId,
            String contextPackVersion,
            List<String> errors,
            AgentAuditMetrics metrics
    ) {
        AgentAuditMetrics m = metrics != null ? metrics : AgentAuditMetrics.empty();
        AiToolAuditStore.AuditEntry entry = new AiToolAuditStore.AuditEntry(
                clip(toolName, 64, "unknown"),
                clip(appId, 128, null),
                clip(actor, 128, null),
                sha256(requestBody),
                clip(status, 16, "UNKNOWN"),
                clip(providerId, 64, null),
                clip(modelId, 128, null),
                clip(contextPackVersion, 64, null),
                errors,
                Instant.now(),
                m.latencyMs(),
                m.promptTokens(),
                m.completionTokens(),
                clip(m.turnId(), 64, null),
                m.stepNo(),
                clip(m.interactionMode(), 16, null),
                clip(m.promptProfile(), 32, null)
        );
        try {
            return store.insert(entry);
        } catch (DataAccessException ex) {
            log.warn("AI tool audit insert failed for tool {}: {}", entry.toolName(), ex.getMessage());
            return -1L;
        }
    }

    static String clip(String value, int max, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return "hash-error";
        }
    }
}
