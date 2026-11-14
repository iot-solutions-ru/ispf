package com.ispf.server.application.api;

import com.ispf.server.application.data.ApplicationMessageRelay;
import com.ispf.server.application.data.ApplicationMessageRelay.Enqueued;
import com.ispf.server.application.data.ApplicationMessageRelay.OutboxMessage;
import com.ispf.server.audit.AuditEventService;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authorized enqueue and status for the platform {@code application_outbox}.
 * Delivery stays on {@code ApplicationOutboxPump}; this does not retry HTTP itself.
 */
@RestController
@RequestMapping("/api/v1/applications/{appId}/outbox")
public class ApplicationOutboxController {

    private final ApplicationMessageRelay relay;
    private final AuditEventService auditEventService;
    private final TenantLocalDataAccessGuard tenantLocalDataAccessGuard;

    public ApplicationOutboxController(
            ApplicationMessageRelay relay,
            AuditEventService auditEventService,
            TenantLocalDataAccessGuard tenantLocalDataAccessGuard
    ) {
        this.relay = relay;
        this.auditEventService = auditEventService;
        this.tenantLocalDataAccessGuard = tenantLocalDataAccessGuard;
    }

    @PostMapping
    public Map<String, Object> enqueue(
            @PathVariable String appId,
            @RequestBody EnqueueRequest request,
            Authentication authentication
    ) {
        requireUser(authentication);
        if (!IspfRoles.isConfigurator(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "configurator role required");
        }
        tenantLocalDataAccessGuard.requireExternalDataAccess(authentication);
        String cleanAppId = required(appId, "appId", 128);
        String connectorId = required(request == null ? null : request.connectorId(), "connectorId", 128);
        String messageId = required(request == null ? null : request.messageId(), "messageId", 256);
        String body = request.body() == null ? "{}" : request.body();
        Enqueued enqueued = relay.enqueueReturning(cleanAppId, connectorId, messageId, body);
        audit(authentication.getName(), cleanAppId, connectorId, messageId, enqueued.created());
        return toMap(enqueued.message(), enqueued.created());
    }

    @GetMapping
    public Map<String, Object> status(
            @PathVariable String appId,
            @RequestParam String connectorId,
            @RequestParam String messageId,
            Authentication authentication
    ) {
        requireUser(authentication);
        tenantLocalDataAccessGuard.requireExternalDataAccess(authentication);
        OutboxMessage message = relay.findMessage(
                required(appId, "appId", 128),
                required(connectorId, "connectorId", 128),
                required(messageId, "messageId", 256)
        )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "outbox message not found"));
        return toMap(message, false);
    }

    private void audit(String actor, String appId, String connectorId, String messageId, boolean created) {
        try {
            auditEventService.logInNewTransaction(
                    "integration",
                    "outbox.enqueue",
                    actor,
                    "outbox",
                    messageId,
                    Map.of("appId", appId, "connectorId", connectorId, "created", created)
            );
        } catch (RuntimeException ex) {
            // A failed audit sink must not create a second outbox row.
        }
    }

    private static void requireUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        }
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is too long");
        }
        return trimmed;
    }

    private static Map<String, Object> toMap(OutboxMessage message, boolean created) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", message.id());
        body.put("messageId", message.messageId());
        body.put("status", message.status());
        body.put("attempts", message.attempts());
        body.put("created", created);
        return body;
    }

    public record EnqueueRequest(String connectorId, String messageId, String body) {
    }
}
