package com.ispf.server.application.data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Delivers an outbox row with HTTP POST. A non-2xx response throws so the relay
 * keeps the row pending and retries. The message id is the idempotency key.
 */
public final class HttpOutboxDelivery implements ApplicationMessageRelay.Delivery {

    private final HttpClient client;
    private final Map<String, URI> endpoints;

    public HttpOutboxDelivery(Map<String, URI> endpoints) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), endpoints);
    }

    HttpOutboxDelivery(HttpClient client, Map<String, URI> endpoints) {
        this.client = client;
        this.endpoints = endpoints;
    }

    public static Map<String, URI> parseEndpoints(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, URI> parsed = new java.util.LinkedHashMap<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int split = trimmed.indexOf('=');
            if (split <= 0 || split == trimmed.length() - 1) {
                throw new IllegalArgumentException("outbox endpoint must be connector=url: " + trimmed);
            }
            parsed.put(trimmed.substring(0, split).trim(), URI.create(trimmed.substring(split + 1).trim()));
        }
        return Map.copyOf(parsed);
    }

    @Override
    public void deliver(String appId, String connectorId, String messageId, String body) {
        URI endpoint = endpoints.get(connectorId);
        if (endpoint == null) {
            throw new IllegalStateException("no HTTP endpoint for connector " + connectorId);
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", messageId)
                .header("X-Ispf-App", appId)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("outbox HTTP interrupted for " + messageId);
        } catch (IOException ex) {
            throw new IllegalStateException("outbox HTTP failed for " + messageId + ": " + ex.getMessage());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("outbox HTTP " + response.statusCode() + " for " + messageId);
        }
    }
}
