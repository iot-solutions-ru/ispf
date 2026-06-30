package com.ispf.server.platform;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.driver.DriverPointMappingParser;
import com.ispf.server.driver.DriverPointMappingParser.Entry;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HaystackExportService {

    public static final String DEFAULT_ROOT_PATH = "root.platform";
    private static final int FORMAT_VERSION = 1;

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public HaystackExportService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportSubtree(String rootPath, boolean includePoints) {
        String normalizedRoot = normalizeRootPath(rootPath);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (!isUnderRoot(node.path(), normalizedRoot)) {
                continue;
            }
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            if (!hasHaystackMetadata(node)) {
                continue;
            }
            rows.add(buildEquipRow(node));
            if (includePoints) {
                Map<String, Entry> pointMappings = parsePointMappings(node);
                for (String variableName : pointVariableNames(node, pointMappings)) {
                    node.getVariable(variableName).ifPresent(variable ->
                            rows.add(buildPointRow(node, variable, pointMappings.get(variableName)))
                    );
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formatVersion", FORMAT_VERSION);
        payload.put("exportedAt", Instant.now().toString());
        payload.put("rootPath", normalizedRoot);
        payload.put("includePoints", includePoints);
        payload.put("rowCount", rows.size());
        payload.put("rows", rows);
        return payload;
    }

    private Map<String, Object> buildEquipRow(PlatformObject node) {
        Map<String, Object> row = baseRow(node);
        row.put("entityKind", "equip");
        mergeHaystackVariables(row, node);
        return row;
    }

    private Map<String, Object> buildPointRow(PlatformObject node, Variable variable, Entry mapping) {
        Map<String, Object> row = baseRow(node);
        row.put("entityKind", "point");
        row.put("variableName", variable.name());
        row.put("id", node.path() + "." + variable.name());
        if (mapping != null && !mapping.dis().isBlank()) {
            row.put("dis", mapping.dis());
        }
        List<String> pointTags = mapping != null && !mapping.haystackTags().isEmpty()
                ? mapping.haystackTags()
                : List.of("point", "sensor", "his");
        row.put("tags", mergeTags(Map.of(), pointTags));
        row.put("curVal", readNumeric(variable));
        String unit = mapping != null && !mapping.unit().isBlank() ? mapping.unit() : readUnit(variable);
        if (!unit.isBlank()) {
            row.put("unit", unit);
        }
        return row;
    }

    private Map<String, Entry> parsePointMappings(PlatformObject node) {
        return DriverPointMappingParser.parse(readString(node, "driverPointMappingsJson"), objectMapper);
    }

    private static Set<String> pointVariableNames(PlatformObject node, Map<String, Entry> pointMappings) {
        Set<String> names = new LinkedHashSet<>();
        for (Variable variable : node.variables().values()) {
            if (variable.historyEnabled()) {
                names.add(variable.name());
            }
        }
        for (Map.Entry<String, Entry> mapping : pointMappings.entrySet()) {
            if (mapping.getValue().hasHaystackMetadata()) {
                names.add(mapping.getKey());
            }
        }
        return names;
    }

    private static Map<String, Object> baseRow(PlatformObject node) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", node.path());
        row.put("path", node.path());
        row.put("dis", node.displayName());
        return row;
    }

    private void mergeHaystackVariables(Map<String, Object> row, PlatformObject node) {
        row.put("haystackRef", readString(node, "haystackRef"));
        row.put("haystackKind", readString(node, "haystackKind"));
        row.put("tags", parseTags(readString(node, "haystackTags")));
    }

    private static boolean hasHaystackMetadata(PlatformObject node) {
        return node.getVariable("haystackTags").isPresent()
                || node.getVariable("haystackRef").isPresent()
                || node.getVariable("haystackKind").isPresent();
    }

    private static String readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .orElse("");
    }

    private static Double readNumeric(Variable variable) {
        return variable.value()
                .map(DataRecord::firstRow)
                .map(row -> row.get("value"))
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).doubleValue())
                .orElse(null);
    }

    private static String readUnit(Variable variable) {
        return variable.value()
                .map(DataRecord::firstRow)
                .map(row -> row.get("unit"))
                .map(Object::toString)
                .filter(unit -> !unit.isBlank())
                .orElse("");
    }

    private Map<String, Boolean> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return mergeTags(Map.of(), tags);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Map<String, Boolean> mergeTags(Map<String, Boolean> base, List<String> extra) {
        Map<String, Boolean> merged = new LinkedHashMap<>(base);
        for (String tag : extra) {
            if (tag != null && !tag.isBlank()) {
                merged.put(tag.trim(), true);
            }
        }
        return merged;
    }

    static String normalizeRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return DEFAULT_ROOT_PATH;
        }
        String trimmed = rootPath.trim();
        if (!trimmed.startsWith("root.")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rootPath must start with root.");
        }
        return trimmed.replaceAll("\\.+$", "");
    }

    static boolean isUnderRoot(String path, String rootPath) {
        return path.equals(rootPath) || path.startsWith(rootPath + ".");
    }
}
