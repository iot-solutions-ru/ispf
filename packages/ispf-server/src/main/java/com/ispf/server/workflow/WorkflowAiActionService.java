package com.ispf.server.workflow;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.openai.OpenAiCompatibleLlmProvider;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.config.AiProperties;
import com.ispf.server.security.PlatformCredentialService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * BPMN AI service tasks (ADR-0049 Wave 2): LLM_COMPLETE and bounded INVOKE_AGENT.
 * {@code modelRef} may be {@code platform-default}, a model id, or a credentials vault path
 * ({@code root...}) with encrypted API key and optional metadata {@code baseUrl}/{@code model}.
 */
@Service
public class WorkflowAiActionService {

    private final AiProperties aiProperties;
    private final PlatformCredentialService credentialService;
    private final ObjectMapper objectMapper;

    public WorkflowAiActionService(
            AiProperties aiProperties,
            PlatformCredentialService credentialService,
            ObjectMapper objectMapper
    ) {
        this.aiProperties = aiProperties;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
    }

    public String llmComplete(String prompt, String modelRef, int timeoutMs) throws WorkflowException {
        if (prompt == null || prompt.isBlank()) {
            throw new WorkflowException("LLM prompt is empty");
        }
        if (!aiProperties.isEnabled() || "noop".equalsIgnoreCase(aiProperties.getProvider())) {
            return "{\"severity\":\"info\",\"reason\":\"AI disabled (noop)\",\"echo\":"
                    + quote(prompt.length() > 200 ? prompt.substring(0, 200) : prompt) + "}";
        }
        try {
            ResolvedLlmEndpoint endpoint = resolveEndpoint(modelRef, timeoutMs);
            LlmProvider provider = new OpenAiCompatibleLlmProvider(
                    endpoint.baseUrl(),
                    endpoint.model(),
                    endpoint.apiKey(),
                    endpoint.timeout()
            );
            LlmResponse response = provider.complete(new LlmRequest(
                    endpoint.model(),
                    List.of(
                            new LlmMessage("system", "You are an OT automation assistant. Reply concisely."),
                            new LlmMessage("user", prompt)
                    ),
                    Math.min(aiProperties.getMaxTokens(), 4096),
                    aiProperties.getTemperature()
            ));
            return response.content() == null ? "" : response.content().trim();
        } catch (LlmException e) {
            throw new WorkflowException("LLM_COMPLETE failed: " + e.getMessage(), e);
        }
    }

    public String invokeAgent(
            String goal,
            String agentMode,
            String toolAllowlist,
            int maxSteps
    ) throws WorkflowException {
        if (goal == null || goal.isBlank()) {
            throw new WorkflowException("Agent goal is empty");
        }
        int steps = Math.max(1, Math.min(maxSteps <= 0 ? 8 : maxSteps, 32));
        String mode = agentMode == null || agentMode.isBlank() ? "ask" : agentMode.toLowerCase(Locale.ROOT);
        String allow = toolAllowlist == null ? "" : toolAllowlist;
        String prompt = """
                Mode: %s
                Max steps: %d
                Allowed tools (do not invent others): %s
                Goal: %s
                Respond with a short JSON object: {"brief":"...","suggestedActions":[],"confidence":0.0}
                """.formatted(mode, steps, allow, goal);
        return llmComplete(prompt, "platform-default", aiProperties.getTimeoutSeconds() * 1000);
    }

    public static String interpolate(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }

    private ResolvedLlmEndpoint resolveEndpoint(String modelRef, int timeoutMs) {
        Duration timeout = timeoutMs > 0
                ? Duration.ofMillis(timeoutMs)
                : Duration.ofSeconds(Math.max(1, aiProperties.getTimeoutSeconds()));

        String baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        String apiKey = platformApiKey();
        String model = aiProperties.getModel();

        if (modelRef != null && !modelRef.isBlank() && !"platform-default".equals(modelRef)) {
            if (modelRef.startsWith("root.")) {
                Optional<String> secret = credentialService.resolveSecret(modelRef);
                if (secret.isPresent() && !secret.get().isBlank()) {
                    apiKey = secret.get();
                }
                Map<String, String> meta = readCredentialMetadata(modelRef);
                if (meta.containsKey("baseUrl") && !meta.get("baseUrl").isBlank()) {
                    baseUrl = meta.get("baseUrl");
                }
                if (meta.containsKey("model") && !meta.get("model").isBlank()) {
                    model = meta.get("model");
                }
            } else {
                model = modelRef;
            }
        }
        return new ResolvedLlmEndpoint(baseUrl, model, apiKey == null ? "" : apiKey, timeout);
    }

    private Map<String, String> readCredentialMetadata(String objectPath) {
        Map<String, Object> described = credentialService.describe(objectPath);
        Object raw = described.get("metadataJson");
        if (raw == null || raw.toString().isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw.toString());
            Map<String, String> meta = new java.util.LinkedHashMap<>();
            if (node != null && node.isObject()) {
                node.properties().forEach(entry -> {
                    if (entry.getValue() != null && entry.getValue().isValueNode()) {
                        meta.put(entry.getKey(), entry.getValue().asString());
                    }
                });
            }
            return meta;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String platformApiKey() {
        String apiKey = aiProperties.getApiKey();
        if ((apiKey == null || apiKey.isBlank()) && aiProperties.getApiKeyEnv() != null) {
            String env = System.getenv(aiProperties.getApiKeyEnv());
            if (env != null && !env.isBlank()) {
                apiKey = env;
            }
        }
        return apiKey;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record ResolvedLlmEndpoint(String baseUrl, String model, String apiKey, Duration timeout) {
    }
}
