package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public final class FederationPathRemapper {

    private static final List<String> WIDGET_PATH_FIELDS = List.of(
            "objectPath",
            "parentPath",
            "objectPathPrefix",
            "rowTargetDashboard",
            "targetDashboardPath",
            "optionsFrom"
    );

    private FederationPathRemapper() {
    }

    public static String unremapLayoutJson(
            String layoutJson,
            String remotePrefix,
            String localRoot,
            ObjectMapper objectMapper
    ) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return layoutJson;
        }
        try {
            JsonNode root = objectMapper.readTree(layoutJson);
            if (!(root instanceof ObjectNode layout)) {
                return layoutJson;
            }
            JsonNode widgets = layout.get("widgets");
            if (widgets instanceof ArrayNode widgetArray) {
                for (JsonNode widget : widgetArray) {
                    if (widget instanceof ObjectNode widgetObject) {
                        unremapWidgetPaths(widgetObject, remotePrefix, localRoot);
                    }
                }
            }
            return objectMapper.writeValueAsString(layout);
        } catch (Exception ex) {
            return layoutJson;
        }
    }

    public static Object unremapLayoutObject(
            Object layout,
            String remotePrefix,
            String localRoot,
            ObjectMapper objectMapper
    ) {
        if (layout == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.valueToTree(layout);
            if (!(root instanceof ObjectNode layoutObject)) {
                return layout;
            }
            JsonNode widgets = layoutObject.get("widgets");
            if (widgets instanceof ArrayNode widgetArray) {
                for (JsonNode widget : widgetArray) {
                    if (widget instanceof ObjectNode widgetObject) {
                        unremapWidgetPaths(widgetObject, remotePrefix, localRoot);
                    }
                }
            }
            return objectMapper.convertValue(layoutObject, Object.class);
        } catch (Exception ex) {
            return layout;
        }
    }

    static String unremapPath(String path, String remotePrefix, String localRoot) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.equals(localRoot) || path.startsWith(localRoot + ".")) {
            String suffix = path.equals(localRoot) ? "" : path.substring(localRoot.length());
            return remotePrefix + suffix;
        }
        return path;
    }

    private static void unremapWidgetPaths(ObjectNode widget, String remotePrefix, String localRoot) {
        for (String field : WIDGET_PATH_FIELDS) {
            JsonNode value = widget.get(field);
            if (value != null && value.isTextual()) {
                widget.put(field, unremapPath(value.asText(), remotePrefix, localRoot));
            }
        }
        JsonNode fieldsJson = widget.get("fieldsJson");
        if (fieldsJson != null && fieldsJson.isTextual() && !fieldsJson.asText().isBlank()) {
            widget.put("fieldsJson", remapFieldsJson(fieldsJson.asText(), remotePrefix, localRoot, true));
        }
    }

    public static String remapLayoutJson(
            String layoutJson,
            String remotePrefix,
            String localRoot,
            ObjectMapper objectMapper
    ) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return layoutJson;
        }
        try {
            JsonNode root = objectMapper.readTree(layoutJson);
            if (!(root instanceof ObjectNode layout)) {
                return layoutJson;
            }
            JsonNode widgets = layout.get("widgets");
            if (widgets instanceof ArrayNode widgetArray) {
                for (JsonNode widget : widgetArray) {
                    if (widget instanceof ObjectNode widgetObject) {
                        remapWidgetPaths(widgetObject, remotePrefix, localRoot);
                    }
                }
            }
            return objectMapper.writeValueAsString(layout);
        } catch (Exception ex) {
            return layoutJson;
        }
    }

    public static Object remapLayoutObject(
            Object layout,
            String remotePrefix,
            String localRoot,
            ObjectMapper objectMapper
    ) {
        if (layout == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.valueToTree(layout);
            if (!(root instanceof ObjectNode layoutObject)) {
                return layout;
            }
            JsonNode widgets = layoutObject.get("widgets");
            if (widgets instanceof ArrayNode widgetArray) {
                for (JsonNode widget : widgetArray) {
                    if (widget instanceof ObjectNode widgetObject) {
                        remapWidgetPaths(widgetObject, remotePrefix, localRoot);
                    }
                }
            }
            return objectMapper.convertValue(layoutObject, Object.class);
        } catch (Exception ex) {
            return layout;
        }
    }

    static String remapPath(String path, String remotePrefix, String localRoot) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String normalizedPrefix = normalizePrefix(remotePrefix);
        if (path.equals(normalizedPrefix) || path.startsWith(normalizedPrefix + ".")) {
            String suffix = path.equals(normalizedPrefix) ? "" : path.substring(normalizedPrefix.length());
            return localRoot + suffix;
        }
        return path;
    }

    private static void remapWidgetPaths(ObjectNode widget, String remotePrefix, String localRoot) {
        for (String field : WIDGET_PATH_FIELDS) {
            JsonNode value = widget.get(field);
            if (value != null && value.isTextual()) {
                widget.put(field, remapPath(value.asText(), remotePrefix, localRoot));
            }
        }
        JsonNode fieldsJson = widget.get("fieldsJson");
        if (fieldsJson != null && fieldsJson.isTextual() && !fieldsJson.asText().isBlank()) {
            widget.put("fieldsJson", remapFieldsJson(fieldsJson.asText(), remotePrefix, localRoot, false));
        }
    }

    private static String remapFieldsJson(String fieldsJson, String remotePrefix, String localRoot) {
        return remapFieldsJson(fieldsJson, remotePrefix, localRoot, false);
    }

    private static String remapFieldsJson(
            String fieldsJson,
            String remotePrefix,
            String localRoot,
            boolean reverse
    ) {
        try {
            ObjectMapper mapper = JsonMapper.builder().build();
            JsonNode fields = mapper.readTree(fieldsJson);
            if (!fields.isArray()) {
                return fieldsJson;
            }
            for (JsonNode field : fields) {
                if (field instanceof ObjectNode fieldObject) {
                    JsonNode optionsFrom = fieldObject.get("optionsFrom");
                    if (optionsFrom != null && optionsFrom.isTextual()) {
                        fieldObject.put(
                                "optionsFrom",
                                reverse
                                        ? unremapPath(optionsFrom.asText(), remotePrefix, localRoot)
                                        : remapPath(optionsFrom.asText(), remotePrefix, localRoot)
                        );
                    }
                }
            }
            return mapper.writeValueAsString(fields);
        } catch (Exception ex) {
            return fieldsJson;
        }
    }

    static String normalizePrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return "root.platform";
        }
        String trimmed = pathPrefix.trim();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    static String localCatalogRoot(String localPath, String remotePath, String remotePrefix) {
        String normalizedPrefix = normalizePrefix(remotePrefix);
        if (!remotePath.equals(normalizedPrefix) && remotePath.startsWith(normalizedPrefix + ".")) {
            String suffix = remotePath.substring(normalizedPrefix.length());
            if (localPath.endsWith(suffix)) {
                return localPath.substring(0, localPath.length() - suffix.length());
            }
        }
        int lastDot = localPath.lastIndexOf('.');
        return lastDot == -1 ? localPath : localPath.substring(0, lastDot);
    }
}
