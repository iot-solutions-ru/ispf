package com.ispf.server.ai.context;

import com.ispf.server.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Query-side embeddings for hybrid ContextPack search.
 * Uses {@code ispf-hash-v1} locally, or OpenAI-compatible {@code /embeddings} when the pack was
 * built with a remote embedding model.
 */
@Component
public class ContextPackEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(ContextPackEmbeddingClient.class);

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ContextPackEmbeddingClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Optional<float[]> embedQuery(String query, String packEmbeddingModel, int packDims) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String model = packEmbeddingModel == null ? "" : packEmbeddingModel.trim();
        if (model.isBlank()) {
            return Optional.empty();
        }
        if (ContextPackHashEmbedder.isHashModel(model)) {
            int dims = packDims > 0 ? packDims : ContextPackHashEmbedder.DIMS;
            return Optional.of(ContextPackHashEmbedder.embed(query, dims));
        }
        return embedViaOpenAiCompatible(query, model);
    }

    private Optional<float[]> embedViaOpenAiCompatible(String query, String model) {
        String baseUrl = normalizeBaseUrl(aiProperties.getBaseUrl());
        if (baseUrl.isBlank()) {
            return Optional.empty();
        }
        String overrideModel = aiProperties.getEmbeddingModel();
        String effectiveModel = overrideModel != null && !overrideModel.isBlank()
                ? overrideModel.trim()
                : model;
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", effectiveModel);
            body.put("input", query);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .timeout(Duration.ofSeconds(Math.max(5, Math.min(60, aiProperties.getTimeoutSeconds()))))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            String apiKey = resolveApiKey();
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("embeddings HTTP {}: {}", response.statusCode(), truncate(response.body()));
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }
            JsonNode embedding = data.get(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                return Optional.empty();
            }
            float[] out = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                out[i] = (float) embedding.get(i).asDouble();
            }
            return Optional.of(out);
        } catch (Exception ex) {
            log.debug("embeddings request failed: {}", ex.toString());
            return Optional.empty();
        }
    }

    private String resolveApiKey() {
        String key = aiProperties.getApiKey();
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String envName = aiProperties.getApiKeyEnv();
        if (envName != null && !envName.isBlank()) {
            String fromEnv = System.getenv(envName.trim());
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv.trim();
            }
        }
        return "";
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String url = raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.endsWith("/v1") && !lower.contains("/v1/")) {
            url = url + "/v1";
        }
        return url;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "…";
    }
}
