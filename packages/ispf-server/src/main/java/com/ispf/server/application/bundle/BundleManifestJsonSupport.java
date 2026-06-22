package com.ispf.server.application.bundle;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses bundle manifest JSON from LLM output or REST bodies into {@link ApplicationBundleDeployService.BundleManifest}.
 */
public final class BundleManifestJsonSupport {

    public record ParseResult(
            ApplicationBundleDeployService.BundleManifest manifest,
            Map<String, Object> artifact
    ) {
    }

    private BundleManifestJsonSupport() {
    }

    public static ParseResult parseWithArtifact(ObjectMapper objectMapper, String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Bundle manifest must be a JSON object");
        }
        return parseWithArtifact(objectMapper, objectMapper.convertValue(root, Map.class));
    }

    @SuppressWarnings("unchecked")
    public static ParseResult parseWithArtifact(ObjectMapper objectMapper, Map<String, Object> raw) {
        Map<String, Object> manifestMap = prepareManifestMap(objectMapper, raw);
        ApplicationBundleDeployService.BundleManifest manifest = toManifest(objectMapper, manifestMap);
        return new ParseResult(manifest, toArtifactMap(manifestMap));
    }

    public static ApplicationBundleDeployService.BundleManifest parse(
            ObjectMapper objectMapper,
            String json
    ) throws Exception {
        return parseWithArtifact(objectMapper, json).manifest();
    }

    public static ApplicationBundleDeployService.BundleManifest parse(
            ObjectMapper objectMapper,
            Map<String, Object> raw
    ) {
        return parseWithArtifact(objectMapper, raw).manifest();
    }

    public static ParseResult mergeAndParseWithArtifact(
            ObjectMapper objectMapper,
            Map<String, Object> baseManifest,
            String generatedJson
    ) throws Exception {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseManifest != null && !baseManifest.isEmpty()) {
            merged.putAll(prepareManifestMap(objectMapper, baseManifest));
        }
        merged.putAll(prepareManifestMap(objectMapper, objectMapper.readValue(generatedJson, Map.class)));
        ApplicationBundleDeployService.BundleManifest manifest = toManifest(objectMapper, merged);
        return new ParseResult(manifest, toArtifactMap(merged));
    }

    public static ApplicationBundleDeployService.BundleManifest mergeAndParse(
            ObjectMapper objectMapper,
            ApplicationBundleDeployService.BundleManifest baseManifest,
            String generatedJson
    ) throws Exception {
        Map<String, Object> base = baseManifest != null
                ? objectMapper.convertValue(baseManifest, Map.class)
                : Map.of();
        return mergeAndParseWithArtifact(objectMapper, base, generatedJson).manifest();
    }

    public static Map<String, Object> defaultBaseMap(String appId) {
        String safe = appId.trim().replace('-', '_').replaceAll("[^a-zA-Z0-9_]", "");
        if (safe.isBlank()) {
            safe = "generated";
        }
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("version", "1.0.0");
        base.put("displayName", humanizeAppId(appId));
        base.put("tablePrefix", "");
        base.put("schemaName", "app_" + safe);
        base.put("migrations", new ArrayList<>());
        base.put("functions", new ArrayList<>());
        base.put("dashboards", new ArrayList<>());
        return base;
    }

    private static Map<String, Object> prepareManifestMap(ObjectMapper objectMapper, Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Bundle manifest is empty");
        }
        Map<String, Object> manifestMap = unwrapManifestMap(raw);
        manifestMap = normalizeKeys(manifestMap);
        repairLlmManifestShape(objectMapper, manifestMap);
        normalizeNullStrings(manifestMap);
        return manifestMap;
    }

    private static ApplicationBundleDeployService.BundleManifest toManifest(
            ObjectMapper objectMapper,
            Map<String, Object> manifestMap
    ) {
        ApplicationBundleDeployService.BundleManifest manifest = objectMapper.convertValue(
                manifestMap,
                ApplicationBundleDeployService.BundleManifest.class
        );
        requireVersion(manifest);
        return manifest;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapManifestMap(Map<String, Object> raw) {
        for (String key : new String[] {"manifest", "bundle", "artifact"}) {
            Object nested = raw.get(key);
            if (nested instanceof Map<?, ?> nestedMap && !nestedMap.isEmpty()) {
                return new LinkedHashMap<>((Map<String, Object>) nestedMap);
            }
        }
        return new LinkedHashMap<>(raw);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeKeys(Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = toCamelCaseKey(entry.getKey());
            normalized.put(key, normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeKeys((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeValue(item));
            }
            return normalized;
        }
        return value;
    }

    static String toCamelCaseKey(String key) {
        if (key == null || !key.contains("_")) {
            return key;
        }
        String[] parts = key.split("_");
        StringBuilder builder = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    private static void normalizeNullStrings(Map<String, Object> manifestMap) {
        for (Map.Entry<String, Object> entry : manifestMap.entrySet()) {
            if (entry.getValue() instanceof String text && text.isBlank()) {
                entry.setValue(null);
            }
        }
    }

    private static void requireVersion(ApplicationBundleDeployService.BundleManifest manifest) {
        if (manifest.version() == null || manifest.version().isBlank()) {
            throw new IllegalArgumentException(
                    "manifest.version is required (semver, e.g. \"1.0.0\"). "
                            + "Generate a complete bundle JSON — do not return null placeholders for schema fields."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static void repairLlmManifestShape(ObjectMapper objectMapper, Map<String, Object> manifestMap) {
        repairMigrationEntries(manifestMap);
        repairFunctionEntries(manifestMap);
        repairDashboardEntries(objectMapper, manifestMap);
    }

    @SuppressWarnings("unchecked")
    private static void repairMigrationEntries(Map<String, Object> manifestMap) {
        Object raw = manifestMap.get("migrations");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        List<Map<String, Object>> repaired = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> migration)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) migration);
            if (copy.get("id") == null && copy.get("name") != null) {
                copy.put("id", String.valueOf(copy.get("name")));
            }
            if ((copy.get("sql") == null || String.valueOf(copy.get("sql")).isBlank())
                    && copy.containsKey("columns")) {
                continue;
            }
            if (copy.get("id") != null && copy.get("sql") != null) {
                repaired.add(copy);
            }
        }
        manifestMap.put("migrations", repaired);
    }

    @SuppressWarnings("unchecked")
    private static void repairFunctionEntries(Map<String, Object> manifestMap) {
        Object raw = manifestMap.get("functions");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        List<Map<String, Object>> repaired = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> function)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) function);
            if (copy.get("functionName") == null && copy.get("name") != null) {
                copy.put("functionName", copy.remove("name"));
            }
            if (copy.get("objectPath") == null || String.valueOf(copy.get("objectPath")).isBlank()) {
                copy.put("objectPath", "root.platform.devices.demo-sensor-01");
            }
            if (copy.get("version") == null) {
                copy.put("version", "1");
            }
            if (copy.get("source") == null && copy.get("body") != null) {
                copy.put("source", Map.of(
                        "type", "script",
                        "body", wrapSqlAsScript(String.valueOf(copy.get("body")))
                ));
                copy.remove("body");
            } else if (copy.get("source") instanceof Map<?, ?> sourceMap) {
                Map<String, Object> source = new LinkedHashMap<>((Map<String, Object>) sourceMap);
                Object body = source.get("body");
                if (body instanceof String text && !text.trim().startsWith("{")) {
                    source.put("type", "script");
                    source.put("body", wrapSqlAsScript(text));
                    copy.put("source", source);
                }
            }
            if (copy.get("functionName") != null && copy.get("source") != null) {
                repaired.add(copy);
            }
        }
        manifestMap.put("functions", repaired);
    }

    private static String wrapSqlAsScript(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        if (trimmed.isBlank()) {
            return "{\"steps\":[{\"type\":\"return\",\"fields\":{\"error_code\":\"OK\",\"error_message\":\"\"}}]}";
        }
        String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"steps\":[{\"type\":\"selectMany\",\"var\":\"rows\",\"sql\":\"" + escaped
                + "\"},{\"type\":\"return\",\"fields\":{\"error_code\":\"OK\",\"error_message\":\"\",\"rows\":\"${rows}\"}}]}";
    }

    @SuppressWarnings("unchecked")
    private static void repairDashboardEntries(ObjectMapper objectMapper, Map<String, Object> manifestMap) {
        Object operatorUi = manifestMap.get("operatorUi");
        if (!(operatorUi instanceof Map<?, ?> operatorMap) || operatorMap.isEmpty()) {
            return;
        }
        Map<String, Object> operator = new LinkedHashMap<>((Map<String, Object>) operatorMap);
        if (operator.get("appId") == null && manifestMap.get("schemaName") != null) {
            String schema = String.valueOf(manifestMap.get("schemaName"));
            operator.put("appId", schema.startsWith("app_") ? schema.substring(4) : schema);
        }
        if (operator.get("title") == null && manifestMap.get("displayName") != null) {
            operator.put("title", manifestMap.get("displayName"));
        }
        manifestMap.put("operatorUi", operator);

        Object nestedDashboards = operator.get("dashboards");
        if (!(nestedDashboards instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        if (manifestMap.get("dashboards") instanceof List<?> existing && !existing.isEmpty()) {
            return;
        }
        List<Map<String, Object>> dashboards = new ArrayList<>();
        String appId = operator.get("appId") != null ? String.valueOf(operator.get("appId")) : "generated";
        int index = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> dashboard)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) dashboard);
            Map<String, Object> mapped = new LinkedHashMap<>();
            String slug = copy.get("name") != null ? String.valueOf(copy.get("name")).replaceAll("\\s+", "-").toLowerCase() : ("main-" + index);
            mapped.put("path", "root.platform." + appId + ".dashboards." + slug);
            mapped.put("title", copy.getOrDefault("name", "Dashboard " + (index + 1)));
            Object layout = copy.get("layoutJson");
            if (layout != null && !(layout instanceof String)) {
                try {
                    mapped.put("layoutJson", objectMapper.writeValueAsString(layout));
                } catch (Exception ignored) {
                    mapped.put("layoutJson", "{\"columns\":12,\"rowHeight\":72,\"widgets\":[]}");
                }
            } else if (layout instanceof String text && !text.isBlank()) {
                mapped.put("layoutJson", text);
            } else {
                mapped.put("layoutJson", "{\"columns\":12,\"rowHeight\":72,\"widgets\":[]}");
            }
            dashboards.add(mapped);
            index++;
        }
        if (!dashboards.isEmpty()) {
            manifestMap.put("dashboards", dashboards);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toArtifactMap(Map<String, Object> manifestMap) {
        return stripNulls(new LinkedHashMap<>(manifestMap));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stripNulls(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> nested = stripNulls(new LinkedHashMap<>((Map<String, Object>) map));
                if (!nested.isEmpty()) {
                    result.put(entry.getKey(), nested);
                }
                continue;
            }
            if (value instanceof List<?> list) {
                if (!list.isEmpty()) {
                    result.put(entry.getKey(), value);
                }
                continue;
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private static String humanizeAppId(String appId) {
        String trimmed = appId == null ? "" : appId.trim();
        if (trimmed.isBlank()) {
            return "Generated App";
        }
        String[] parts = trimmed.split("[-_]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "Generated App" : builder.toString();
    }
}
