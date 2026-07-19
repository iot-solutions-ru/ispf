package com.ispf.server.ai.agent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight JSON Schema subset validator for agent tool arguments (ADR-0051).
 * Supports: type object, properties, required, enum, additionalProperties, and
 * primitive types string/boolean/integer/number/object/array.
 */
public final class AgentToolSchemaValidator {

    public static final String DOC_REF = "decisions/0051-poka-yoke-constraints-over-guards.md";

    private AgentToolSchemaValidator() {
    }

    public record Violation(String code, String path, String hint) {
        Map<String, Object> toErrorResult() {
            return Map.of(
                    "status", "ERROR",
                    "error", code + (path != null && !path.isBlank() ? " at " + path : ""),
                    "code", code,
                    "path", path != null ? path : "",
                    "hint", hint != null ? hint : "",
                    "docRef", DOC_REF
            );
        }
    }

    @SuppressWarnings("unchecked")
    public static Optional<Violation> validate(Map<String, Object> schema, Map<String, Object> arguments) {
        if (schema == null || schema.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> args = arguments != null ? arguments : Map.of();
        Object type = schema.get("type");
        if (type != null && !"object".equals(String.valueOf(type))) {
            return Optional.of(new Violation(
                    "SCHEMA_TYPE_UNSUPPORTED",
                    "",
                    "Tool inputSchema must have type=object"
            ));
        }
        Object propsRaw = schema.get("properties");
        Map<String, Object> properties = propsRaw instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        Object requiredRaw = schema.get("required");
        if (requiredRaw instanceof Collection<?> required) {
            for (Object req : required) {
                String key = String.valueOf(req);
                if (!args.containsKey(key) || isBlankValue(args.get(key))) {
                    return Optional.of(new Violation(
                            "REQUIRED_ARG_MISSING",
                            key,
                            "Provide required argument '" + key + "'"
                    ));
                }
            }
        }

        boolean additional = schema.get("additionalProperties") instanceof Boolean b ? b : true;
        if (!additional) {
            for (String key : args.keySet()) {
                if (!properties.containsKey(key)) {
                    return Optional.of(new Violation(
                            "UNKNOWN_ARG",
                            key,
                            "Argument '" + key + "' is not declared in inputSchema; remove it or use a declared property"
                    ));
                }
            }
        }

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            if (!properties.containsKey(key)) {
                continue;
            }
            if (isBlankValue(entry.getValue())) {
                // Optional blank args are ignored (same as omitted)
                continue;
            }
            Object propRaw = properties.get(key);
            if (!(propRaw instanceof Map<?, ?> propMap)) {
                continue;
            }
            Map<String, Object> prop = (Map<String, Object>) propMap;
            Optional<Violation> typeViolation = checkType(key, entry.getValue(), prop);
            if (typeViolation.isPresent()) {
                return typeViolation;
            }
            Optional<Violation> enumViolation = checkEnum(key, entry.getValue(), prop);
            if (enumViolation.isPresent()) {
                return enumViolation;
            }
        }
        return Optional.empty();
    }

    private static Optional<Violation> checkType(String key, Object value, Map<String, Object> prop) {
        if (value == null) {
            return Optional.empty();
        }
        Object type = prop.get("type");
        if (type == null) {
            return Optional.empty();
        }
        String expected = String.valueOf(type).toLowerCase(Locale.ROOT);
        boolean ok = switch (expected) {
            case "string" -> value instanceof String || value instanceof Number || value instanceof Boolean;
            case "boolean" -> value instanceof Boolean
                    || "true".equalsIgnoreCase(String.valueOf(value))
                    || "false".equalsIgnoreCase(String.valueOf(value));
            case "integer" -> value instanceof Integer || value instanceof Long
                    || (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()))
                    || (value instanceof String s && s.matches("-?\\d+"));
            case "number" -> value instanceof Number || (value instanceof String s && s.matches("-?\\d+(\\.\\d+)?"));
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?> || value instanceof Object[];
            default -> true;
        };
        if (ok) {
            return Optional.empty();
        }
        return Optional.of(new Violation(
                "ARG_TYPE_MISMATCH",
                key,
                "Argument '" + key + "' must be " + expected + ", got " + value.getClass().getSimpleName()
        ));
    }

    @SuppressWarnings("unchecked")
    private static Optional<Violation> checkEnum(String key, Object value, Map<String, Object> prop) {
        Object enumRaw = prop.get("enum");
        if (!(enumRaw instanceof Collection<?> allowed) || allowed.isEmpty() || value == null) {
            return Optional.empty();
        }
        String asString = String.valueOf(value);
        for (Object option : allowed) {
            if (asString.equals(String.valueOf(option))) {
                return Optional.empty();
            }
        }
        return Optional.of(new Violation(
                "ARG_ENUM_MISMATCH",
                key,
                "Argument '" + key + "' must be one of " + allowed + ", got '" + asString + "'"
        ));
    }

    private static boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    /** True when schema is the historic MCP stub (open object, no properties). */
    public static boolean isOpenStub(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return true;
        }
        Object props = schema.get("properties");
        boolean emptyProps = !(props instanceof Map<?, ?> map) || map.isEmpty();
        boolean additional = !(schema.get("additionalProperties") instanceof Boolean b) || b;
        return emptyProps && additional && schema.get("required") == null;
    }
}
