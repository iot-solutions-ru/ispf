package com.ispf.server.notification;

import com.ispf.server.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NotificationDispatchService(NotificationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                .build();
    }

    public void sendWebhook(String url, Map<String, Object> context) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL is required");
        }
        postJson(url.trim(), context);
    }

    public void sendEmail(String target, Map<String, Object> context) {
        EmailTarget parsed = EmailTarget.parse(target);
        String relay = properties.getEmailRelayUrl();
        if (relay == null || relay.isBlank()) {
            throw new IllegalStateException(
                    "Email relay not configured (ispf.notifications.email-relay-url). "
                            + "Use SEND_WEBHOOK or configure relay."
            );
        }
        Map<String, Object> payload = new LinkedHashMap<>(context);
        payload.put("to", parsed.to());
        payload.put("subject", parsed.subject());
        payload.put("body", parsed.body());
        postJson(relay.trim(), payload);
    }

    public Map<String, Object> baseContext(
            String source,
            String sourceId,
            String objectPath,
            String eventName
    ) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("source", source);
        ctx.put("sourceId", sourceId);
        ctx.put("objectPath", objectPath);
        ctx.put("eventName", eventName);
        ctx.put("timestamp", Instant.now().toString());
        return ctx;
    }

    private void postJson(String url, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Notification HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Notification dispatch failed for {}: {}", url, ex.getMessage());
            throw new IllegalStateException("Notification dispatch failed: " + ex.getMessage(), ex);
        }
    }

    private record EmailTarget(String to, String subject, String body) {
        static EmailTarget parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("Email target is required (to@host|subject|body)");
            }
            String[] parts = raw.split("\\|", 3);
            if (parts.length < 3 || parts[0].isBlank()) {
                throw new IllegalArgumentException("Email target format: to@host|subject|body");
            }
            return new EmailTarget(parts[0].trim(), parts[1].trim(), parts[2]);
        }
    }
}
