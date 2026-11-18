package com.ispf.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ispf.ai.LlmException;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmToolCall;
import com.ispf.ai.LlmUsage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Incremental OpenAI SSE and Ollama NDJSON chat completions.
 * Callbacks receive the accumulated visible text (assistant content, or the first tool call).
 */
public final class LlmChatStream {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PREVIEW_CHARS = 4_000;
    /** Text before the visible tail that is still masked, so a secret value cut by the tail stays hidden. */
    private static final int MASK_LOOKBACK_CHARS = 4_000;
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(\"[^\"]*(?:password|passwd|secret|token|api[_-]?key|authorization|credential|private[_-]?key|client[_-]?secret)[^\"]*\"\\s*:\\s*\")((?:[^\"\\\\]|\\\\[\\s\\S])*+\\\\?)(\"|\\z)"
    );

    private LlmChatStream() {
    }

    public static LlmResponse parseOpenAiSse(
            String sseBody,
            String fallbackModel,
            Consumer<String> onPreview
    ) throws LlmException {
        return readOpenAiSse(sseBody == null ? "" : sseBody, fallbackModel, onPreview, null);
    }

    public static LlmResponse parseOllamaNdjson(
            String ndjson,
            String fallbackModel,
            Consumer<String> onPreview
    ) throws LlmException {
        return readOllamaNdjson(ndjson == null ? "" : ndjson, fallbackModel, onPreview, null);
    }

    public static LlmResponse readOpenAiSse(
            InputStream input,
            String fallbackModel,
            Consumer<String> onPreview,
            BooleanSupplier cancelled
    ) throws LlmException {
        AtomicBoolean finished = new AtomicBoolean(false);
        Thread watcher = watchCancel(input, cancelled, finished);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return consumeOpenAi(reader, fallbackModel, onPreview, cancelled);
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isCancelled(cancelled)) {
                throw new LlmException("Agent run cancelled", ex);
            }
            throw new LlmException("Failed to read LLM stream: " + ex.getMessage(), ex);
        } finally {
            finished.set(true);
            if (watcher != null) {
                watcher.interrupt();
            }
        }
    }

    public static LlmResponse readOllamaNdjson(
            InputStream input,
            String fallbackModel,
            Consumer<String> onPreview,
            BooleanSupplier cancelled
    ) throws LlmException {
        AtomicBoolean finished = new AtomicBoolean(false);
        Thread watcher = watchCancel(input, cancelled, finished);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return consumeOllama(reader, fallbackModel, onPreview, cancelled);
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isCancelled(cancelled)) {
                throw new LlmException("Agent run cancelled", ex);
            }
            throw new LlmException("Failed to read Ollama stream: " + ex.getMessage(), ex);
        } finally {
            finished.set(true);
            if (watcher != null) {
                watcher.interrupt();
            }
        }
    }

    public static ObjectNode withStream(ObjectNode body, boolean stream) {
        if (body != null && stream) {
            body.put("stream", true);
        }
        return body;
    }

    private static LlmResponse readOpenAiSse(
            String sseBody,
            String fallbackModel,
            Consumer<String> onPreview,
            BooleanSupplier cancelled
    ) throws LlmException {
        return consumeOpenAi(new BufferedReader(new java.io.StringReader(sseBody)), fallbackModel, onPreview, cancelled);
    }

    private static LlmResponse readOllamaNdjson(
            String ndjson,
            String fallbackModel,
            Consumer<String> onPreview,
            BooleanSupplier cancelled
    ) throws LlmException {
        return consumeOllama(new BufferedReader(new java.io.StringReader(ndjson)), fallbackModel, onPreview, cancelled);
    }

    private static LlmResponse consumeOpenAi(
            BufferedReader reader,
            String fallbackModel,
            Consumer<String> onPreview,
            BooleanSupplier cancelled
    ) throws LlmException {
        Accumulator acc = new Accumulator();
        StringBuilder plain = new StringBuilder();
        boolean sawData = false;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled(cancelled)) {
                    throw new LlmException("Agent run cancelled");
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                    continue;
                }
                if (!trimmed.startsWith("data:")) {
                    plain.append(trimmed).append('\n');
                    continue;
                }
                sawData = true;
                String data = trimmed.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                applyOpenAiChunk(acc, data);
                emit(onPreview, acc);
            }
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isCancelled(cancelled)) {
                throw new LlmException("Agent run cancelled", ex);
            }
            throw new LlmException("Failed to parse LLM stream: " + ex.getMessage(), ex);
        }
        if (isCancelled(cancelled)) {
            throw new LlmException("Agent run cancelled");
        }
        if (!sawData) {
            String body = plain.toString().trim();
            if (body.startsWith("{")) {
                try {
                    JsonNode root = MAPPER.readTree(body);
                    throwIfProviderError(root);
                    LlmResponse parsed = LlmHttpSupport.parseChatCompletion(body, fallbackModel);
                    if (onPreview != null && parsed.content() != null && !parsed.content().isBlank()) {
                        onPreview.accept(tail(parsed.content()));
                    }
                    return parsed;
                } catch (LlmException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new LlmException("Failed to parse LLM response: " + ex.getMessage(), ex);
                }
            }
            if (!body.isEmpty()) {
                throw new LlmException("LLM stream was not SSE or JSON");
            }
        }
        return acc.toResponse(fallbackModel);
    }

    private static LlmResponse consumeOllama(
            BufferedReader reader,
            String fallbackModel,
            Consumer<String> onPreview,
            BooleanSupplier cancelled
    ) throws LlmException {
        Accumulator acc = new Accumulator();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled(cancelled)) {
                    throw new LlmException("Agent run cancelled");
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                boolean done = applyOllamaChunk(acc, trimmed);
                emit(onPreview, acc);
                if (done) {
                    break;
                }
            }
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isCancelled(cancelled)) {
                throw new LlmException("Agent run cancelled", ex);
            }
            throw new LlmException("Failed to parse Ollama stream: " + ex.getMessage(), ex);
        }
        if (isCancelled(cancelled)) {
            throw new LlmException("Agent run cancelled");
        }
        return acc.toResponse(fallbackModel);
    }

    private static void applyOpenAiChunk(Accumulator acc, String data) throws Exception {
        JsonNode root = MAPPER.readTree(data);
        throwIfProviderError(root);
        if (root.hasNonNull("model")) {
            acc.model = root.path("model").asText(acc.model);
        }
        if (root.has("usage") && root.get("usage").isObject()) {
            acc.usage = usage(root.get("usage"));
        }
        JsonNode choice = root.path("choices").isArray() && !root.path("choices").isEmpty()
                ? root.path("choices").get(0)
                : null;
        if (choice == null) {
            return;
        }
        String finish = choice.path("finish_reason").asText(null);
        if (finish != null && !finish.isBlank() && !"null".equals(finish)) {
            acc.finishReason = finish;
        }
        JsonNode delta = choice.path("delta");
        if (delta.isMissingNode() || delta.isNull()) {
            delta = choice.path("message");
        }
        if (delta.has("content") && !delta.get("content").isNull()) {
            acc.content.append(delta.get("content").asText(""));
        }
        JsonNode toolCalls = delta.path("tool_calls");
        if (toolCalls.isArray()) {
            for (JsonNode call : toolCalls) {
                int index = call.path("index").asInt(0);
                ToolAcc tool = acc.tool(index);
                if (call.hasNonNull("id") && !call.path("id").asText("").isBlank()) {
                    tool.id = call.path("id").asText();
                }
                JsonNode function = call.path("function");
                if (function.has("name") && !function.path("name").asText("").isBlank()) {
                    tool.name.append(function.path("name").asText(""));
                }
                if (function.has("arguments") && !function.get("arguments").isNull()) {
                    appendArguments(tool, function.get("arguments"));
                }
            }
        }
    }

    private static boolean applyOllamaChunk(Accumulator acc, String line) throws Exception {
        JsonNode root = MAPPER.readTree(line);
        throwIfProviderError(root);
        if (root.hasNonNull("model")) {
            acc.model = root.path("model").asText(acc.model);
        }
        JsonNode message = root.path("message");
        if (message.has("content") && !message.get("content").isNull()) {
            acc.content.append(message.path("content").asText(""));
        }
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            int index = 0;
            for (JsonNode call : toolCalls) {
                ToolAcc tool = acc.tool(index++);
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                if (!name.isBlank()) {
                    tool.name.setLength(0);
                    tool.name.append(name);
                }
                if (function.has("arguments") && !function.get("arguments").isNull()) {
                    JsonNode args = function.get("arguments");
                    tool.args.setLength(0);
                    tool.args.append(args.isTextual() ? args.asText("") : MAPPER.writeValueAsString(args));
                }
            }
        }
        return root.path("done").asBoolean(false);
    }

    private static void emit(Consumer<String> onPreview, Accumulator acc) {
        if (onPreview == null) {
            return;
        }
        String preview = acc.preview();
        if (!preview.isBlank()) {
            onPreview.accept(preview);
        }
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        return cancelled != null && cancelled.getAsBoolean();
    }

    private static LlmUsage usage(JsonNode node) {
        return new LlmUsage(
                node.path("prompt_tokens").isNumber() ? node.path("prompt_tokens").asInt() : null,
                node.path("completion_tokens").isNumber() ? node.path("completion_tokens").asInt() : null,
                node.path("total_tokens").isNumber() ? node.path("total_tokens").asInt() : null
        );
    }

    public static String redactedPreview(String text) {
        return tail(text == null ? "" : text);
    }

    private static String tail(CharSequence text) {
        int from = Math.max(0, text.length() - (PREVIEW_CHARS + MASK_LOOKBACK_CHARS));
        String masked = maskSecrets(text.subSequence(from, text.length()).toString());
        if (from == 0 && masked.length() <= PREVIEW_CHARS) {
            return masked;
        }
        return "…" + masked.substring(Math.max(0, masked.length() - (PREVIEW_CHARS - 1)));
    }

    private static String maskSecrets(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return SECRET_FIELD.matcher(text).replaceAll("$1***$3");
    }

    private static void appendArguments(ToolAcc tool, JsonNode args) throws Exception {
        if (args.isTextual()) {
            tool.args.append(args.asText(""));
            return;
        }
        tool.args.setLength(0);
        tool.args.append(MAPPER.writeValueAsString(args));
    }

    private static void throwIfProviderError(JsonNode root) throws LlmException {
        if (root == null || !root.has("error") || root.get("error").isNull()) {
            return;
        }
        JsonNode error = root.get("error");
        String message = error.isTextual() ? error.asText("") : error.path("message").asText("");
        if (message == null || message.isBlank()) {
            message = error.toString();
        }
        throw new LlmException(message);
    }

    private static Thread watchCancel(InputStream input, BooleanSupplier cancelled, AtomicBoolean finished) {
        if (input == null || cancelled == null) {
            return null;
        }
        Thread watcher = new Thread(() -> {
            while (!finished.get()) {
                if (cancelled.getAsBoolean()) {
                    try {
                        input.close();
                    } catch (Exception ignored) {
                        // Closing the socket unblocks readLine on the agent thread.
                    }
                    return;
                }
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }, "llm-stream-cancel");
        watcher.setDaemon(true);
        watcher.start();
        return watcher;
    }

    private static final class Accumulator {
        private final StringBuilder content = new StringBuilder();
        private final Map<Integer, ToolAcc> tools = new TreeMap<>();
        private String model;
        private String finishReason;
        private LlmUsage usage;

        private ToolAcc tool(int index) {
            return tools.computeIfAbsent(index, ignored -> new ToolAcc());
        }

        private String preview() {
            if (!content.isEmpty()) {
                return tail(content);
            }
            if (tools.isEmpty()) {
                return "";
            }
            ToolAcc tool = tools.values().iterator().next();
            String name = tool.name.toString();
            String args = tool.args.toString();
            if (name.isBlank() && args.isBlank()) {
                return "";
            }
            String visible = name.isBlank() ? "tool" : "tool " + name;
            if (!args.isBlank()) {
                visible = visible + " " + args;
            }
            return tail(visible);
        }

        private LlmResponse toResponse(String fallbackModel) {
            List<LlmToolCall> calls = new ArrayList<>();
            for (ToolAcc tool : tools.values()) {
                String name = tool.name.toString();
                if (name.isBlank()) {
                    continue;
                }
                String id = tool.id == null || tool.id.isBlank() ? "tool_call_" + calls.size() : tool.id;
                String args = tool.args.isEmpty() ? "{}" : tool.args.toString();
                calls.add(new LlmToolCall(id, name, args));
            }
            String resolvedModel = model != null && !model.isBlank() ? model : fallbackModel;
            return new LlmResponse(content.toString(), resolvedModel, usage, finishReason, calls);
        }
    }

    private static final class ToolAcc {
        private String id;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder args = new StringBuilder();
    }
}
