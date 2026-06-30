package com.ispf.server.ai.agent;

import com.ispf.server.api.dto.ObjectDto;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Blocks tree mutations until the agent has discovered real paths and catalog entries via tools —
 * not from playbooks, recipes, or invented names.
 */
final class AgentGroundTruthGuard {

    private static final Set<String> TREE_DISCOVERY_TOOLS = Set.of(
            "list_objects",
            "get_object",
            "search_objects",
            "search_by_haystack_tags"
    );

    private static final Set<String> MODEL_DISCOVERY_TOOLS = Set.of(
            "list_relative_models",
            "list_instance_types",
            "list_absolute_models",
            "list_object_models",
            "get_object_model",
            "list_virtual_profiles"
    );

    private AgentGroundTruthGuard() {
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    static Optional<BlockDecision> checkBeforeTool(
            String toolName,
            Map<String, Object> arguments,
            List<Map<String, Object>> steps
    ) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return switch (toolName.toLowerCase(Locale.ROOT)) {
            case "create_object", "create_virtual_device" -> checkParentDiscovered(arguments, steps);
            case "apply_relative_model" -> checkModelDiscovered(arguments, steps, "modelName", "modelId");
            case "instantiate_instance_type" -> checkModelDiscovered(arguments, steps, "instanceType", "modelName", "modelId");
            case "ensure_absolute_instance" -> checkModelDiscovered(arguments, steps, "modelName", "modelId");
            default -> Optional.empty();
        };
    }

    private static Optional<BlockDecision> checkParentDiscovered(
            Map<String, Object> arguments,
            List<Map<String, Object>> steps
    ) {
        String parentPath = stringArg(arguments, "parentPath");
        if (parentPath.isBlank()) {
            parentPath = stringArg(arguments, "parent");
        }
        if (parentPath.isBlank()) {
            return Optional.empty();
        }
        if (isParentGrounded(steps, parentPath)) {
            return Optional.empty();
        }
        return Optional.of(new BlockDecision(
                "Cannot " + mutationLabel(arguments) + ": parent path was not discovered in this turn: " + parentPath,
                "Call list_objects parent=" + parentPath
                        + " (lists that exact folder) or list_objects parent=" + parentOf(parentPath)
                        + " and confirm the folder appears in objects[].path — then create_object with that parentPath."
        ));
    }

    private static String mutationLabel(Map<String, Object> arguments) {
        return stringArg(arguments, "name").isBlank() ? "create" : "create under " + stringArg(arguments, "name");
    }

    private static Optional<BlockDecision> checkModelDiscovered(
            Map<String, Object> arguments,
            List<Map<String, Object>> steps,
            String... modelArgKeys
    ) {
        String modelRef = "";
        for (String key : modelArgKeys) {
            modelRef = stringArg(arguments, key);
            if (!modelRef.isBlank()) {
                break;
            }
        }
        if (modelRef.isBlank()) {
            return Optional.empty();
        }
        if (isModelGrounded(steps, modelRef)) {
            return Optional.empty();
        }
        return Optional.of(new BlockDecision(
                "Cannot apply model: \"" + modelRef + "\" was not returned by a catalog tool in this turn",
                "Call list_relative_models (RELATIVE), list_instance_types (INSTANCE), "
                        + "list_absolute_models (ABSOLUTE), or list_virtual_profiles — then pick modelName / "
                        + "profile / instanceType only from that tool result."
        ));
    }

    static boolean isParentGrounded(List<Map<String, Object>> steps, String parentPath) {
        if (steps == null || steps.isEmpty() || parentPath == null || parentPath.isBlank()) {
            return false;
        }
        String normalized = normalizePath(parentPath);
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");

            if ("create_object".equals(tool)) {
                String createdPath = normalizePath(String.valueOf(result.get("path")));
                if (!createdPath.isBlank() && (normalized.equals(createdPath) || normalized.startsWith(createdPath + "."))) {
                    return true;
                }
            }
            if ("create_virtual_device".equals(tool)) {
                String createdPath = normalizePath(String.valueOf(result.get("path")));
                String createdParent = parentOf(createdPath);
                if (normalized.equals(createdParent)) {
                    return true;
                }
            }
            if (TREE_DISCOVERY_TOOLS.contains(tool)) {
                if (parentMatchesDiscovery(normalized, tool, args, result)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isModelGrounded(List<Map<String, Object>> steps, String modelRef) {
        if (steps == null || steps.isEmpty() || modelRef == null || modelRef.isBlank()) {
            return false;
        }
        String needle = modelRef.trim().toLowerCase(Locale.ROOT);
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!MODEL_DISCOVERY_TOOLS.contains(tool)) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            if (catalogResultContainsModel(result, needle, tool)) {
                return true;
            }
        }
        return false;
    }

    private static boolean parentMatchesDiscovery(
            String parentPath,
            String tool,
            Map<String, Object> args,
            Map<String, Object> result
    ) {
        if ("get_object".equals(tool)) {
            String path = normalizePath(stringArg(args, "path"));
            return parentPath.equals(path) || parentPath.startsWith(path + ".");
        }
        if ("list_objects".equals(tool)) {
            String listedParent = listedParentFromArgs(args);
            if (parentPath.equals(listedParent)) {
                return true;
            }
            if (parentPath.startsWith(listedParent + ".")) {
                if (objectListContainsPath(result, parentPath)) {
                    return true;
                }
                String remainder = parentPath.substring(listedParent.length() + 1);
                if (!remainder.contains(".")) {
                    return objectListContainsSegment(result, listedParent, remainder);
                }
                String firstSegment = remainder.substring(0, remainder.indexOf('.'));
                if (!objectListContainsSegment(result, listedParent, firstSegment)) {
                    return false;
                }
                // Ancestor folder visible — not enough for deeper parent; need list_objects on intermediate path
                return objectListContainsPath(result, parentPath);
            }
        }
        if ("search_objects".equals(tool) || "search_by_haystack_tags".equals(tool)) {
            return objectListContainsPath(result, parentPath);
        }
        return false;
    }

    private static boolean catalogResultContainsModel(Map<String, Object> result, String needle, String tool) {
        if ("list_virtual_profiles".equals(tool)) {
            return listContainsField(result, "profiles", "profile", needle)
                    || listContainsField(result, "profiles", "templateId", needle);
        }
        return listContainsField(result, "models", "modelName", needle)
                || listContainsField(result, "models", "name", needle)
                || listContainsField(result, "models", "modelId", needle)
                || listContainsField(result, "instanceTypes", "modelName", needle)
                || listContainsField(result, "instanceTypes", "name", needle)
                || stringFieldEquals(result, "modelName", needle)
                || stringFieldEquals(result, "name", needle);
    }

    @SuppressWarnings("unchecked")
    private static boolean listContainsField(
            Map<String, Object> result,
            String listKey,
            String field,
            String needle
    ) {
        Object listObj = result.get(listKey);
        if (!(listObj instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                Object value = row.get(field);
                if (value != null && needle.equals(String.valueOf(value).trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean stringFieldEquals(Map<String, Object> result, String field, String needle) {
        Object value = result.get(field);
        return value != null && needle.equals(String.valueOf(value).trim().toLowerCase(Locale.ROOT));
    }

    private static String listedParentFromArgs(Map<String, Object> args) {
        String listedParent = normalizePath(stringArg(args, "parent"));
        if (listedParent.isBlank()) {
            listedParent = normalizePath(stringArg(args, "parentPath"));
        }
        if (listedParent.isBlank()) {
            listedParent = "root";
        }
        return listedParent;
    }

    @SuppressWarnings("unchecked")
    private static List<?> objectsList(Map<String, Object> result) {
        Object listObj = result.get("objects");
        return listObj instanceof List<?> list ? list : List.of();
    }

    private static Optional<String> rowPath(Object item) {
        if (item instanceof ObjectDto dto) {
            return Optional.ofNullable(dto.path()).filter(path -> !path.isBlank());
        }
        if (item instanceof Map<?, ?> row) {
            Object path = row.get("path");
            if (path != null && !String.valueOf(path).isBlank()) {
                return Optional.of(String.valueOf(path));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> rowName(Object item) {
        if (item instanceof ObjectDto dto) {
            String path = dto.path();
            if (path != null && path.contains(".")) {
                return Optional.of(path.substring(path.lastIndexOf('.') + 1));
            }
            return Optional.ofNullable(dto.displayName()).filter(name -> !name.isBlank());
        }
        if (item instanceof Map<?, ?> row) {
            Object rowName = row.get("name");
            if (rowName != null && !String.valueOf(rowName).isBlank()) {
                return Optional.of(String.valueOf(rowName));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static boolean objectListContainsSegment(
            Map<String, Object> result,
            String listedParent,
            String segment
    ) {
        String needle = segment.trim().toLowerCase(Locale.ROOT);
        String expectedPath = normalizePath(listedParent + "." + segment);
        for (Object item : objectsList(result)) {
            Optional<String> pathOpt = rowPath(item);
            if (pathOpt.isPresent()) {
                String rowPath = normalizePath(pathOpt.get());
                if (rowPath.equals(expectedPath) || rowPath.endsWith("." + segment)) {
                    return true;
                }
            }
            Optional<String> nameOpt = rowName(item);
            if (nameOpt.isPresent() && needle.equals(nameOpt.get().trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean objectListContainsName(Map<String, Object> result, String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (Object item : objectsList(result)) {
            Optional<String> nameOpt = rowName(item);
            if (nameOpt.isPresent() && needle.equals(nameOpt.get().trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
            Optional<String> pathOpt = rowPath(item);
            if (pathOpt.isPresent() && normalizePath(pathOpt.get()).endsWith("." + name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean objectListContainsPath(Map<String, Object> result, String path) {
        String needle = normalizePath(path);
        for (Object item : objectsList(result)) {
            Optional<String> pathOpt = rowPath(item);
            if (pathOpt.isPresent() && needle.equals(normalizePath(pathOpt.get()))) {
                return true;
            }
        }
        return false;
    }

    private static String parentOf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot <= 0) {
            return "";
        }
        return path.substring(0, lastDot);
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replaceAll("\\.+", ".").replaceAll("\\.$", "");
    }

    private static boolean isOkToolStep(Map<String, Object> step) {
        if (!"tool".equals(String.valueOf(step.get("type")))) {
            return false;
        }
        Map<String, Object> result = stepMap(step, "result");
        return "OK".equals(String.valueOf(result.get("status")));
    }

    private static String toolName(Map<String, Object> step) {
        return String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return "";
        }
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
