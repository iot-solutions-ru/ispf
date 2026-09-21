package com.ispf.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ispf.ai.LlmContentPart;
import com.ispf.ai.LlmException;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmModelInfo;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmToolCall;
import com.ispf.ai.LlmToolSpec;
import com.ispf.ai.LlmUsage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LlmHttpSupport {

    /** 1×1 PNG for vision capability probes (minimal valid image). */
    public static final String VISION_PROBE_PNG_DATA_URI =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmHttpSupport() {
    }

    public static HttpClient client(Duration timeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    public static String getJson(
            HttpClient client,
            Duration timeout,
            String url,
            Map<String, String> headers
    ) throws LlmException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET();
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException(formatHttpStatusFailure(url, response.statusCode(), response.body()));
            }
            return response.body();
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException(formatHttpFailure(url, ex), ex);
        }
    }

    public record HttpResult(int statusCode, String body) {
    }

    public static String postJson(
            HttpClient client,
            Duration timeout,
            String url,
            Map<String, String> headers,
            ObjectNode body
    ) throws LlmException {
        HttpResult result = postJsonWithStatus(client, timeout, url, headers, body);
        if (result.statusCode() < 200 || result.statusCode() >= 300) {
            throw new LlmException(formatHttpStatusFailure(url, result.statusCode(), result.body()));
        }
        return result.body();
    }

    public static HttpResult postJsonWithStatus(
            HttpClient client,
            Duration timeout,
            String url,
            Map<String, String> headers,
            ObjectNode body
    ) throws LlmException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (Exception ex) {
            throw new LlmException(formatHttpFailure(url, ex), ex);
        }
    }

    public static ObjectNode chatCompletionBody(LlmRequest request) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", request.model() != null ? request.model() : "");
        ArrayNode messages = root.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode item = chatMessageNode(message, false);
            messages.add(item);
        }
        addTools(root, request, false);
        if (request.maxTokens() != null) {
            root.put("max_tokens", request.maxTokens());
        }
        if (request.temperature() != null) {
            root.put("temperature", request.temperature());
        }
        if (request.providerOptions() != null && !request.providerOptions().isEmpty()) {
            mergeProviderOptions(root, request.providerOptions());
        }
        return root;
    }

    private static ObjectNode chatMessageNode(LlmMessage message, boolean ollamaArgumentsAsObject) {
        ObjectNode item = MAPPER.createObjectNode();
        item.put("role", message.role());
        if ("tool".equals(message.role()) && message.toolCallId() != null && !message.toolCallId().isBlank()) {
            item.put("tool_call_id", message.toolCallId());
        }
        if (message.hasToolCalls()) {
            ArrayNode toolCalls = item.putArray("tool_calls");
            for (LlmToolCall toolCall : message.toolCalls()) {
                ObjectNode call = toolCalls.addObject();
                if (toolCall.id() != null && !toolCall.id().isBlank()) {
                    call.put("id", toolCall.id());
                }
                call.put("type", "function");
                ObjectNode function = call.putObject("function");
                function.put("name", toolCall.name() != null ? toolCall.name() : "");
                if (ollamaArgumentsAsObject) {
                    function.set("arguments", parseJsonOrText(toolCall.argumentsJson()));
                } else {
                    function.put("arguments", toolCall.argumentsJson() != null ? toolCall.argumentsJson() : "{}");
                }
            }
        }
            if (message.hasMultimodalParts()) {
                ArrayNode contentParts = item.putArray("content");
                for (LlmContentPart part : message.parts()) {
                    if (part.isText() && part.text() != null && !part.text().isBlank()) {
                        ObjectNode textPart = contentParts.addObject();
                        textPart.put("type", "text");
                        textPart.put("text", part.text());
                    } else if (part.isImageUrl() && part.imageUrl() != null && !part.imageUrl().isBlank()) {
                        ObjectNode imagePart = contentParts.addObject();
                        imagePart.put("type", "image_url");
                        ObjectNode imageUrl = imagePart.putObject("image_url");
                        imageUrl.put("url", part.imageUrl());
                    }
                }
            } else {
                item.put("content", message.content() != null ? message.content() : "");
            }
        return item;
    }

    private static void addTools(ObjectNode root, LlmRequest request, boolean ollama) {
        if (request.tools() == null || request.tools().isEmpty()) {
            return;
        }
        ArrayNode tools = root.putArray("tools");
        for (LlmToolSpec tool : request.tools()) {
            ObjectNode item = tools.addObject();
            item.put("type", "function");
            ObjectNode function = item.putObject("function");
            function.put("name", tool.name() != null ? tool.name() : "");
            function.put("description", tool.description() != null ? tool.description() : "");
            function.set("parameters", MAPPER.valueToTree(tool.parameters()));
        }
        if (!ollama && request.toolChoice() != null && !request.toolChoice().isBlank()) {
            root.put("tool_choice", request.toolChoice());
        }
        if (ollama && request.toolChoice() != null && !request.toolChoice().isBlank()) {
            root.put("tool_choice", request.toolChoice());
        }
    }

    private static void mergeProviderOptions(ObjectNode root, Map<String, Object> providerOptions) {
        for (Map.Entry<String, Object> entry : providerOptions.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                ObjectNode child = root.putObject(entry.getKey());
                for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                    String key = String.valueOf(nestedEntry.getKey());
                    Object nestedValue = nestedEntry.getValue();
                    if (nestedValue instanceof Boolean bool) {
                        child.put(key, bool);
                    } else if (nestedValue instanceof Integer intValue) {
                        child.put(key, intValue);
                    } else if (nestedValue instanceof Long longValue) {
                        child.put(key, longValue);
                    } else if (nestedValue instanceof Double doubleValue) {
                        child.put(key, doubleValue);
                    } else if (nestedValue != null) {
                        child.put(key, nestedValue.toString());
                    }
                }
            } else {
                root.putPOJO(entry.getKey(), value);
            }
        }
    }

    public static ObjectNode ollamaChatBody(LlmRequest request) {
        ObjectNode root = chatCompletionBody(request);
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode messages = root.putArray("messages");
            for (LlmMessage message : request.messages()) {
                messages.add(chatMessageNode(message, true));
            }
        }
        root.put("stream", false);
        return root;
    }

    public static LlmResponse parseChatCompletion(String json, String fallbackModel) throws LlmException {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new LlmException("LLM response missing choices");
            }
            JsonNode firstChoice = choices.get(0);
            String content = firstChoice.path("message").path("content").asText("");
            String model = root.path("model").asText(fallbackModel);
            String finishReason = firstChoice.path("finish_reason").asText(null);
            if (finishReason != null && finishReason.isBlank()) {
                finishReason = null;
            }
            List<LlmToolCall> toolCalls = parseToolCalls(firstChoice.path("message").path("tool_calls"));
            return new LlmResponse(content, model, parseUsage(root.path("usage")), finishReason, toolCalls);
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse LLM response: " + ex.getMessage(), ex);
        }
    }

    public static LlmResponse parseOllamaChat(String json, String fallbackModel) throws LlmException {
        try {
            JsonNode root = MAPPER.readTree(json);
            String content = root.path("message").path("content").asText("");
            List<LlmToolCall> toolCalls = parseToolCalls(root.path("message").path("tool_calls"));
            return new LlmResponse(content, fallbackModel, null, null, toolCalls);
        } catch (Exception ex) {
            throw new LlmException("Failed to parse Ollama response: " + ex.getMessage(), ex);
        }
    }

    public static List<LlmModelInfo> parseOpenAiModels(String json) throws LlmException {
        try {
            JsonNode data = MAPPER.readTree(json).path("data");
            List<LlmModelInfo> models = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText("");
                    if (!id.isBlank()) {
                        models.add(new LlmModelInfo(id, id));
                    }
                }
            }
            return models;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse models response: " + ex.getMessage(), ex);
        }
    }

    public static List<LlmModelInfo> parseOllamaModels(String json) throws LlmException {
        try {
            JsonNode models = MAPPER.readTree(json).path("models");
            List<LlmModelInfo> result = new ArrayList<>();
            if (models.isArray()) {
                for (JsonNode item : models) {
                    String name = item.path("name").asText("");
                    if (!name.isBlank()) {
                        result.add(new LlmModelInfo(name, name));
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse Ollama models: " + ex.getMessage(), ex);
        }
    }

    public static ObjectNode ollamaShowBody(String model) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model != null ? model : "");
        return root;
    }

    public static boolean parseOllamaShowVision(String json) throws LlmException {
        try {
            JsonNode capabilities = MAPPER.readTree(json).path("capabilities");
            if (!capabilities.isArray()) {
                return false;
            }
            for (JsonNode capability : capabilities) {
                if ("vision".equalsIgnoreCase(capability.asText(""))) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse Ollama show response: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads vision capability from OpenAI-compatible {@code GET /models} when present
     * (e.g. OpenRouter {@code architecture.modality}, {@code input_modalities}).
     *
     * @return {@code null} when the model entry has no modality metadata
     */
    public static Boolean visionFromOpenAiModelsList(String json, String modelId) throws LlmException {
        try {
            JsonNode data = MAPPER.readTree(json).path("data");
            if (!data.isArray() || modelId == null || modelId.isBlank()) {
                return null;
            }
            for (JsonNode item : data) {
                if (!modelId.equals(item.path("id").asText(""))) {
                    continue;
                }
                Boolean fromModality = visionFromModalityText(item.path("architecture").path("modality").asText(null));
                if (fromModality != null) {
                    return fromModality;
                }
                JsonNode inputModalities = item.path("input_modalities");
                if (inputModalities.isArray()) {
                    for (JsonNode modality : inputModalities) {
                        if ("image".equalsIgnoreCase(modality.asText(""))) {
                            return true;
                        }
                    }
                    return false;
                }
                JsonNode capabilities = item.path("capabilities");
                if (capabilities.isObject()) {
                    JsonNode vision = capabilities.path("vision");
                    if (vision.isBoolean()) {
                        return vision.asBoolean();
                    }
                }
                return null;
            }
            return null;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse models vision metadata: " + ex.getMessage(), ex);
        }
    }

    public static LlmRequest visionProbeRequest(String model) {
        return new LlmRequest(
                model,
                List.of(new LlmMessage(
                        "user",
                        "ping",
                        List.of(
                                LlmContentPart.text("ping"),
                                LlmContentPart.imageUrl(VISION_PROBE_PNG_DATA_URI)
                        )
                )),
                1,
                0.0
        );
    }

    /**
     * Interprets a chat-completions probe response: 2xx = vision supported;
     * 4xx with vision-related error text = not supported.
     */
    public static boolean interpretVisionProbeResult(int statusCode, String body) throws LlmException {
        if (statusCode >= 200 && statusCode < 300) {
            return true;
        }
        if (statusCode >= 400 && statusCode < 500) {
            if (looksLikeVisionRejection(body)) {
                return false;
            }
            throw new LlmException("Vision probe HTTP " + statusCode + ": " + truncate(body));
        }
        throw new LlmException("Vision probe HTTP " + statusCode + ": " + truncate(body));
    }

    private static Boolean visionFromModalityText(String modality) {
        if (modality == null || modality.isBlank()) {
            return null;
        }
        String normalized = modality.toLowerCase(Locale.ROOT);
        if (normalized.contains("image")) {
            return true;
        }
        if (normalized.contains("text")) {
            return false;
        }
        return null;
    }

    private static boolean looksLikeVisionRejection(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("vision")
                || lower.contains("image")
                || lower.contains("multimodal")
                || lower.contains("modality")
                || lower.contains("does not support")
                || lower.contains("not support")
                || lower.contains("unsupported")
                || lower.contains("invalid content")
                || lower.contains("expected text");
    }

    private static LlmUsage parseUsage(JsonNode usageNode) {
        if (!usageNode.isObject()) {
            return null;
        }
        return new LlmUsage(
                intOrNull(usageNode.path("prompt_tokens")),
                intOrNull(usageNode.path("completion_tokens")),
                intOrNull(usageNode.path("total_tokens"))
        );
    }

    private static Integer intOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private static List<LlmToolCall> parseToolCalls(JsonNode node) throws Exception {
        if (!node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<LlmToolCall> calls = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            JsonNode function = item.path("function");
            String name = function.path("name").asText("");
            if (name.isBlank()) {
                name = item.path("name").asText("");
            }
            if (name.isBlank()) {
                continue;
            }
            String id = item.path("id").asText("");
            if (id.isBlank()) {
                id = "tool_call_" + index;
            }
            JsonNode argsNode = function.has("arguments") ? function.get("arguments") : item.get("arguments");
            String argumentsJson = argumentsJson(argsNode);
            calls.add(new LlmToolCall(id, name, argumentsJson));
            index++;
        }
        return List.copyOf(calls);
    }

    private static String argumentsJson(JsonNode node) throws Exception {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "{}";
        }
        if (node.isTextual()) {
            String text = node.asText();
            return text == null || text.isBlank() ? "{}" : text;
        }
        return MAPPER.writeValueAsString(node);
    }

    private static JsonNode parseJsonOrText(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ignored) {
            return MAPPER.getNodeFactory().textNode(json);
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }

    /**
     * OpenAI-compatible clients POST {@code {base}/chat/completions}. Host-only URLs
     * (and a pasted full completions path) otherwise hit nginx HTML 404.
     */
    public static String normalizeOpenAiCompatibleBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/chat/completions")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/chat/completions".length());
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            return trimmed;
        }
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return trimmed + "/v1";
        }
        return trimmed;
    }

    static String formatHttpStatusFailure(String url, int statusCode, String body) {
        String suffix = url == null || url.isBlank() ? "" : " url=" + url;
        if (looksLikeHtmlGatewayPage(body)) {
            String hint = statusCode == 404
                    ? " nginx HTML 404 (not an LLM JSON API). Set ISPF_AI_BASE_URL / ispf.ai.base-url"
                    + " to the OpenAI-compatible root including /v1 (e.g. https://api.deepseek.com/v1),"
                    + " not the ISPF web console."
                    : " gateway returned HTML instead of JSON.";
            return "HTTP " + statusCode + ":" + hint + suffix;
        }
        return "HTTP " + statusCode + ": " + truncate(body) + suffix;
    }

    private static boolean looksLikeHtmlGatewayPage(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.stripLeading().toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype html")
                || lower.startsWith("<html")
                || lower.contains("<center>nginx</center>");
    }

    static String formatHttpFailure(String url, Exception ex) {
        String detail = exceptionDetail(ex);
        String suffix = url == null || url.isBlank() ? "" : " url=" + url;
        return "LLM HTTP request failed: " + detail + suffix;
    }

    private static String exceptionDetail(Throwable ex) {
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause != ex) {
            String causeDetail = exceptionDetail(cause);
            if (!causeDetail.equals(cause.getClass().getSimpleName())) {
                return ex.getClass().getSimpleName() + " (" + cause.getClass().getSimpleName() + ": " + causeDetail + ")";
            }
            return ex.getClass().getSimpleName() + " (" + cause.getClass().getSimpleName() + ")";
        }
        return ex.getClass().getSimpleName();
    }
}
