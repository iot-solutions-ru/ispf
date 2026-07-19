package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema helpers for agent tool {@code inputSchema} (ADR-0051 poka-yoke).
 */
public final class AgentToolSchemas {

    private AgentToolSchemas() {
    }

    public static Map<String, Object> stringProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        if (description != null && !description.isBlank()) {
            prop.put("description", description);
        }
        return prop;
    }

    public static Map<String, Object> booleanProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "boolean");
        if (description != null && !description.isBlank()) {
            prop.put("description", description);
        }
        return prop;
    }

    public static Map<String, Object> integerProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "integer");
        if (description != null && !description.isBlank()) {
            prop.put("description", description);
        }
        return prop;
    }

    public static Map<String, Object> numberProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "number");
        if (description != null && !description.isBlank()) {
            prop.put("description", description);
        }
        return prop;
    }

    public static Map<String, Object> objectProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "object");
        prop.put("additionalProperties", true);
        if (description != null && !description.isBlank()) {
            prop.put("description", description);
        }
        return prop;
    }

    public static Map<String, Object> arrayProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "array");
        if (description != null && !description.isBlank()) {
            prop.put("description", description);
        }
        return prop;
    }

    public static Map<String, Object> enumProp(String description, List<String> values) {
        Map<String, Object> prop = stringProp(description);
        prop.put("enum", List.copyOf(values));
        return prop;
    }

    /**
     * @param additionalProperties {@code true} allows undeclared keys (soft); {@code false} rejects them
     */
    public static Map<String, Object> objectSchema(
            Map<String, Map<String, Object>> properties,
            List<String> required,
            boolean additionalProperties
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        if (properties != null) {
            properties.forEach(props::put);
        }
        schema.put("properties", props);
        if (required != null && !required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        schema.put("additionalProperties", additionalProperties);
        return schema;
    }

    public static Map<String, Object> emptyObject() {
        return objectSchema(Map.of(), List.of(), false);
    }

    /** Soft open object — last resort; prefer declared properties. */
    public static Map<String, Object> openObject(String description) {
        Map<String, Object> schema = objectSchema(Map.of(), List.of(), true);
        if (description != null && !description.isBlank()) {
            schema.put("description", description);
        }
        return schema;
    }

    public static Map<String, Map<String, Object>> props(Object... keyPropPairs) {
        if (keyPropPairs.length % 2 != 0) {
            throw new IllegalArgumentException("props requires even key/prop pairs");
        }
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (int i = 0; i < keyPropPairs.length; i += 2) {
            String key = String.valueOf(keyPropPairs[i]);
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) keyPropPairs[i + 1];
            map.put(key, prop);
        }
        return map;
    }

    public static List<String> req(String... names) {
        List<String> list = new ArrayList<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                list.add(name);
            }
        }
        return list;
    }
}
