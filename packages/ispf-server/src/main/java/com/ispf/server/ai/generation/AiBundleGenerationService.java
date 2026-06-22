package com.ispf.server.ai.generation;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.ai.validation.BundleValidationResult;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleManifestJsonSupport;
import com.ispf.server.config.AiProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiBundleGenerationService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final ContextPackService contextPackService;
    private final AiToolRegistry toolRegistry;
    private final AiToolAuditService auditService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiBundleGenerationService(
            LlmProviderRegistry llmProviderRegistry,
            ContextPackService contextPackService,
            AiToolRegistry toolRegistry,
            AiToolAuditService auditService,
            AiProperties aiProperties,
            ObjectMapper objectMapper
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.contextPackService = contextPackService;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> generate(
            String appId,
            String prompt,
            ApplicationBundleDeployService.BundleManifest baseManifest,
            String actor
    ) throws Exception {
        if (!llmProviderRegistry.isGenerationAvailable()) {
            throw new IllegalStateException(
                    "LLM provider is not configured. Set ispf.ai.provider and base-url/model."
            );
        }

        String systemPrompt = buildSystemPrompt(appId);
        LlmResponse response = llmProviderRegistry.complete(new LlmRequest(
                aiProperties.getModel(),
                List.of(
                        new LlmMessage("system", systemPrompt),
                        new LlmMessage("user", prompt)
                ),
                aiProperties.getMaxTokens(),
                aiProperties.getTemperature()
        ));

        BundleManifestJsonSupport.ParseResult parsed = parseBundleManifest(
                response.content(),
                appId,
                baseManifest
        );
        ApplicationBundleDeployService.BundleManifest generated = parsed.manifest();
        Map<String, Object> artifact = parsed.artifact();

        Map<String, Object> validation = toolRegistry.validateBundle(appId, generated, actor);
        Map<String, Object> dryRun = toolRegistry.dryRunDeploy(appId, generated, actor);

        boolean publishable = BundleValidationResult.OK.equals(validation.get("status"))
                && BundleValidationResult.OK.equals(dryRun.get("status"));

        long auditId = auditService.record(
                "generate_bundle",
                appId,
                actor,
                prompt,
                publishable ? BundleValidationResult.OK : BundleValidationResult.ERROR,
                llmProviderRegistry.activeProvider().providerId(),
                response.model(),
                contextPackService.contextPackVersion(),
                mergeErrors(validation, dryRun)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifact", artifact);
        result.put("validation", validation);
        result.put("dryRun", dryRun);
        result.put("publishable", publishable);
        result.put("auditId", auditId);
        result.put("provider", llmProviderRegistry.status());
        if (!publishable) {
            result.put("llmPreview", truncate(response.content(), 2000));
        }
        return result;
    }

    private List<String> mergeErrors(Map<String, Object> validation, Map<String, Object> dryRun) {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        appendErrors(errors, validation);
        appendErrors(errors, dryRun);
        return errors;
    }

    @SuppressWarnings("unchecked")
    private void appendErrors(List<String> errors, Map<String, Object> section) {
        Object raw = section.get("errors");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                errors.add(String.valueOf(item));
            }
        }
    }

    private String buildSystemPrompt(String appId) {
        return """
                You are an ISPF solution developer assistant.
                Generate ONLY one JSON object — a complete bundle manifest.
                Do NOT output Java, React, markdown fences, or prose.
                Use camelCase field names exactly as in the example (displayName, schemaName, objectPath, functionName, layoutJson).
                Never set fields to null. Omit unused sections instead of null placeholders.
                Target appId: %s
                schemaName should be app_<appId> with hyphens replaced by underscores.
                Required: version (semver e.g. 1.0.0), displayName, schemaName, and at least one of migrations[], functions[], dashboards[], operatorUi.
                migrations[] entries MUST use {id, sql}. functions[] MUST use {objectPath, functionName, version, source:{type,body}}.
                Reference example (adapt to the user prompt):
                %s
                """.formatted(appId, referenceExampleManifest());
    }

    private String referenceExampleManifest() {
        try {
            return new ClassPathResource("warehouse-bundle.json")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .trim();
        } catch (Exception ex) {
            return "{}";
        }
    }

    private BundleManifestJsonSupport.ParseResult parseBundleManifest(
            String content,
            String appId,
            ApplicationBundleDeployService.BundleManifest baseManifest
    ) throws Exception {
        String json = extractJsonObject(content);
        Map<String, Object> base = baseManifest != null
                ? objectMapper.convertValue(baseManifest, Map.class)
                : BundleManifestJsonSupport.defaultBaseMap(appId);
        return BundleManifestJsonSupport.mergeAndParseWithArtifact(objectMapper, base, json);
    }

    String extractJsonObject(String content) throws Exception {
        String trimmed = content != null ? content.trim() : "";
        String bestCandidate = null;
        int bestScore = -1;

        for (int start = trimmed.indexOf('{'); start >= 0; start = trimmed.indexOf('{', start + 1)) {
            int end = findJsonObjectEnd(trimmed, start);
            if (end < 0) {
                continue;
            }

            String candidate = trimmed.substring(start, end + 1);
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (!node.isObject() || node.isEmpty()) {
                    continue;
                }
                int score = node.size();
                if (node.has("version")) {
                    score += 20;
                }
                if (node.has("migrations")) {
                    score += 10;
                }
                if (node.has("functions")) {
                    score += 10;
                }
                if (node.has("operatorUi")) {
                    score += 10;
                }
                if (node.has("schemaName")) {
                    score += 5;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = objectMapper.writeValueAsString(node);
                }
            } catch (Exception ignored) {
                // Keep scanning in case prose before the real manifest contains brace-delimited text.
            }
        }

        if (bestCandidate == null) {
            throw new IllegalArgumentException("LLM response does not contain a JSON object");
        }
        return bestCandidate;
    }

    private int findJsonObjectEnd(String content, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }
}
