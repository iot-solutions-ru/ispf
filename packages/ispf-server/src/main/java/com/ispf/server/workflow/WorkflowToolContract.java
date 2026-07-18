package com.ispf.server.workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight required-property validation for workflow tool contracts (ADR-0049).
 */
public final class WorkflowToolContract {

    private WorkflowToolContract() {
    }

    public static void validateInput(ObjectMapper mapper, String inputSchemaJson, Map<String, String> input)
            throws WorkflowToolContractException {
        if (inputSchemaJson == null || inputSchemaJson.isBlank() || "{}".equals(inputSchemaJson.trim())) {
            return;
        }
        try {
            JsonNode schema = mapper.readTree(inputSchemaJson);
            JsonNode required = schema.get("required");
            if (required == null || !required.isArray()) {
                return;
            }
            Map<String, String> safe = input == null ? Map.of() : input;
            for (JsonNode node : required) {
                String key = node.asText();
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (!safe.containsKey(key) || safe.get(key) == null || safe.get(key).isBlank()) {
                    throw new WorkflowToolContractException("Missing required input: " + key);
                }
            }
        } catch (WorkflowToolContractException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowToolContractException("Invalid inputSchemaJson: " + e.getMessage(), e);
        }
    }

    public static Map<String, String> extractOutput(
            ObjectMapper mapper,
            String outputSchemaJson,
            Map<String, String> variables
    ) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> vars = variables == null ? Map.of() : variables;
        if (outputSchemaJson == null || outputSchemaJson.isBlank() || "{}".equals(outputSchemaJson.trim())) {
            out.putAll(vars);
            return out;
        }
        try {
            JsonNode schema = mapper.readTree(outputSchemaJson);
            JsonNode properties = schema.get("properties");
            if (properties == null || !properties.isObject()) {
                out.putAll(vars);
                return out;
            }
            for (String name : properties.propertyNames()) {
                if (vars.containsKey(name)) {
                    out.put(name, vars.get(name));
                }
            }
            return out;
        } catch (Exception e) {
            out.putAll(vars);
            return out;
        }
    }

    public static class WorkflowToolContractException extends Exception {
        public WorkflowToolContractException(String message) {
            super(message);
        }

        public WorkflowToolContractException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
