package com.ispf.server.audit;

import com.ispf.server.config.AuditProperties;
import com.ispf.server.security.OutboundUrlSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fire-and-forget SIEM webhook for security audit events (BL-156).
 */
@Component
public class AuditSiemWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AuditSiemWebhookDispatcher.class);

    private final AuditProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-siem-webhook");
        t.setDaemon(true);
        return t;
    });

    public AuditSiemWebhookDispatcher(AuditProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getSiemTimeoutSeconds())))
                .build();
    }

    public void dispatch(AuditEventService.AuditEvent event) {
        if (!properties.isSiemWebhookEnabled() || event == null) {
            return;
        }
        if (properties.isSiemAsync()) {
            executor.execute(() -> postQuietly(event));
        } else {
            postQuietly(event);
        }
    }

    void postQuietly(AuditEventService.AuditEvent event) {
        try {
            URI uri = OutboundUrlSafety.requireSafeHttpUrl(properties.getSiemWebhookUrl(), "", false);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", event.id());
            payload.put("category", event.category());
            payload.put("action", event.action());
            payload.put("actor", event.actor());
            payload.put("targetType", event.targetType());
            payload.put("targetId", event.targetId());
            payload.put("detailsJson", event.detailsJson());
            payload.put("occurredAt", event.occurredAt() != null ? event.occurredAt().toString() : null);
            payload.put("source", "ispf-audit");
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(properties.getSiemTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-ISPF-Audit-Event", "true")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("SIEM audit webhook HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("SIEM audit webhook failed: {}", ex.getMessage());
        }
    }
}
