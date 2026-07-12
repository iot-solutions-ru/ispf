package com.ispf.server.function;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed {@code sourceBody} for {@code sourceType=expression} functions.
 * Plain string or JSON with optional Tier B formula metadata.
 */
public record FunctionExpressionBody(
        String expression,
        String formulaRef,
        Map<String, String> formulaParams,
        String formulaScope,
        String formulaAppId
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public boolean hasFormulaRef() {
        return formulaRef != null && !formulaRef.isBlank();
    }

    public static FunctionExpressionBody parse(String sourceBody) {
        if (sourceBody == null || sourceBody.isBlank()) {
            throw new IllegalArgumentException("Expression function body is blank");
        }
        String trimmed = sourceBody.trim();
        if (!trimmed.startsWith("{")) {
            return new FunctionExpressionBody(trimmed, null, Map.of(), null, null);
        }
        try {
            JsonNode root = MAPPER.readTree(trimmed);
            String expression = textOrNull(root, "expression");
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("Expression function JSON requires non-blank expression");
            }
            Map<String, String> params = Map.of();
            if (root.has("formulaParams") && root.get("formulaParams").isObject()) {
                params = MAPPER.convertValue(root.get("formulaParams"), new TypeReference<>() {});
            }
            return new FunctionExpressionBody(
                    expression.trim(),
                    textOrNull(root, "formulaRef"),
                    params != null ? params : Map.of(),
                    textOrNull(root, "formulaScope"),
                    textOrNull(root, "formulaAppId")
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid expression function JSON: " + ex.getMessage(), ex);
        }
    }

    public String serialize() {
        if (!hasFormulaRef() && (formulaParams == null || formulaParams.isEmpty())
                && (formulaScope == null || formulaScope.isBlank())
                && (formulaAppId == null || formulaAppId.isBlank())) {
            return expression;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expression", expression);
        if (hasFormulaRef()) {
            payload.put("formulaRef", formulaRef);
        }
        if (formulaParams != null && !formulaParams.isEmpty()) {
            payload.put("formulaParams", formulaParams);
        }
        if (formulaScope != null && !formulaScope.isBlank()) {
            payload.put("formulaScope", formulaScope);
        }
        if (formulaAppId != null && !formulaAppId.isBlank()) {
            payload.put("formulaAppId", formulaAppId);
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize expression function body", ex);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
