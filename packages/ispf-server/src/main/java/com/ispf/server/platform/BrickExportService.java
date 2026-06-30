package com.ispf.server.platform;

import tools.jackson.databind.ObjectMapper;
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
public class BrickExportService {

    public static final String DEFAULT_ROOT_PATH = HaystackExportService.DEFAULT_ROOT_PATH;
    public static final String BRICK_NS = "https://brickschema.org/schema/Brick#";
    public static final String ISPF_URN_PREFIX = "urn:ispf:platform:";
    private static final int FORMAT_VERSION = 1;

    private static final String DEFAULT_POINT_CLASS = BRICK_NS + "Sensor";
    private static final String TEMPERATURE_POINT_CLASS = BRICK_NS + "Temperature_Sensor";

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public BrickExportService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportJsonLd(String rootPath, boolean includePoints) {
        String normalizedRoot = HaystackExportService.normalizeRootPath(rootPath);
        List<Map<String, Object>> graph = buildGraph(normalizedRoot, includePoints);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formatVersion", FORMAT_VERSION);
        payload.put("format", "jsonld");
        payload.put("exportedAt", Instant.now().toString());
        payload.put("rootPath", normalizedRoot);
        payload.put("includePoints", includePoints);
        payload.put("entityCount", graph.size());
        payload.put("@context", Map.of(
                "brick", BRICK_NS,
                "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
                "ispf", ISPF_URN_PREFIX
        ));
        payload.put("@graph", graph);
        return payload;
    }

    @Transactional(readOnly = true)
    public String exportTurtle(String rootPath, boolean includePoints) {
        String normalizedRoot = HaystackExportService.normalizeRootPath(rootPath);
        List<Map<String, Object>> graph = buildGraph(normalizedRoot, includePoints);
        StringBuilder ttl = new StringBuilder();
        ttl.append("@prefix brick: <").append(BRICK_NS).append("> .\n");
        ttl.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");
        for (Map<String, Object> node : graph) {
            String id = (String) node.get("@id");
            String type = expandType((String) node.get("@type"));
            ttl.append("<").append(id).append("> a ").append(type).append(" ");
            Object label = node.get("rdfs:label");
            if (label != null && !label.toString().isBlank()) {
                ttl.append(";\n    rdfs:label \"").append(escapeTurtleLiteral(label.toString())).append("\" ");
            }
            Object unit = node.get("brick:hasUnit");
            if (unit != null && !unit.toString().isBlank()) {
                ttl.append(";\n    brick:hasUnit \"").append(escapeTurtleLiteral(unit.toString())).append("\" ");
            }
            Object hasPoint = node.get("brick:hasPoint");
            if (hasPoint instanceof List<?> points) {
                for (Object pointRef : points) {
                    if (pointRef instanceof Map<?, ?> refMap) {
                        Object pointId = refMap.get("@id");
                        if (pointId != null) {
                            ttl.append(";\n    brick:hasPoint <").append(pointId).append("> ");
                        }
                    }
                }
            }
            ttl.append(" .\n");
        }
        return ttl.toString();
    }

    private List<Map<String, Object>> buildGraph(String normalizedRoot, boolean includePoints) {
        List<Map<String, Object>> graph = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (!HaystackExportService.isUnderRoot(node.path(), normalizedRoot)) {
                continue;
            }
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            String brickClass = readString(node, "brickClass");
            if (brickClass.isBlank()) {
                continue;
            }
            Map<String, Object> equipNode = new LinkedHashMap<>();
            equipNode.put("@id", entityIri(node.path()));
            equipNode.put("@type", compactType(resolveBrickClass(brickClass)));
            equipNode.put("rdfs:label", node.displayName());
            equipNode.put("ispf:path", node.path());

            List<Map<String, String>> pointRefs = new ArrayList<>();
            if (includePoints) {
                Map<String, Entry> pointMappings = parsePointMappings(node);
                for (String variableName : pointVariableNames(node, pointMappings)) {
                    Entry mapping = pointMappings.get(variableName);
                    Map<String, Object> pointNode = new LinkedHashMap<>();
                    String pointIri = entityIri(node.path(), variableName);
                    pointNode.put("@id", pointIri);
                    pointNode.put("@type", compactType(resolvePointBrickClass(mapping)));
                    String dis = mapping != null && !mapping.dis().isBlank()
                            ? mapping.dis()
                            : variableName;
                    pointNode.put("rdfs:label", dis);
                    pointNode.put("ispf:path", node.path() + "." + variableName);
                    if (mapping != null && !mapping.unit().isBlank()) {
                        pointNode.put("brick:hasUnit", mapping.unit());
                    }
                    graph.add(pointNode);
                    pointRefs.add(Map.of("@id", pointIri));
                }
            }
            if (!pointRefs.isEmpty()) {
                equipNode.put("brick:hasPoint", pointRefs);
            }
            graph.add(equipNode);
        }
        return graph;
    }

    static String entityIri(String objectPath) {
        return entityIri(objectPath, null);
    }

    static String entityIri(String objectPath, String variableName) {
        String suffix = objectPath.startsWith("root.") ? objectPath.substring("root.".length()) : objectPath;
        suffix = suffix.replace('.', '/');
        if (variableName != null && !variableName.isBlank()) {
            suffix = suffix + "/" + variableName;
        }
        return ISPF_URN_PREFIX + suffix;
    }

    static String resolveBrickClass(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_POINT_CLASS;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("urn:")) {
            return trimmed;
        }
        if (trimmed.contains(":")) {
            if (trimmed.startsWith("brick:")) {
                return BRICK_NS + trimmed.substring("brick:".length());
            }
            return trimmed;
        }
        return BRICK_NS + trimmed;
    }

    static String resolvePointBrickClass(Entry mapping) {
        if (mapping != null && mapping.haystackTags().stream().anyMatch(tag -> "temp".equalsIgnoreCase(tag))) {
            return TEMPERATURE_POINT_CLASS;
        }
        return DEFAULT_POINT_CLASS;
    }

    static String compactType(String typeIri) {
        if (typeIri.startsWith(BRICK_NS)) {
            return "brick:" + typeIri.substring(BRICK_NS.length());
        }
        return typeIri;
    }

    static String expandType(String compactType) {
        if (compactType.startsWith("brick:")) {
            return "brick:" + compactType.substring("brick:".length());
        }
        return compactType;
    }

    private static String escapeTurtleLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private static String readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .orElse("");
    }

    public static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "jsonld";
        }
        String normalized = format.trim().toLowerCase();
        if (!normalized.equals("jsonld") && !normalized.equals("turtle") && !normalized.equals("ttl")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "format must be jsonld, turtle, or ttl."
            );
        }
        return normalized.equals("ttl") ? "turtle" : normalized;
    }
}
