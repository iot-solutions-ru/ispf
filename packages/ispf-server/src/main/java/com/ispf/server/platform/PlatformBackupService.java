package com.ispf.server.platform;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.federation.FederationProxyMetadata;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlatformBackupService {

    public static final String ROOT_PATH = "root.platform";
    private static final int FORMAT_VERSION = 1;

    private final ObjectManager objectManager;
    private final ObjectEntityMapper entityMapper;
    private final ObjectMapper objectMapper;

    public PlatformBackupService(
            ObjectManager objectManager,
            ObjectEntityMapper entityMapper,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.entityMapper = entityMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportSubtree() {
        List<Map<String, Object>> nodes = objectManager.tree().all().stream()
                .filter(node -> node.path().equals(ROOT_PATH) || node.path().startsWith(ROOT_PATH + "."))
                .sorted(Comparator.comparing(PlatformObject::path))
                .map(this::serializeNode)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formatVersion", FORMAT_VERSION);
        payload.put("exportedAt", Instant.now().toString());
        payload.put("rootPath", ROOT_PATH);
        payload.put("nodeCount", nodes.size());
        payload.put("nodes", nodes);
        return payload;
    }

    @Transactional(readOnly = true)
    public ImportPreview previewImport(String json) {
        BackupDocument document = parseDocument(json);
        int createCount = 0;
        int updateCount = 0;
        List<String> warnings = new ArrayList<>();
        for (BackupNode node : document.nodes()) {
            if (node.path().equals(ROOT_PATH)) {
                continue;
            }
            Optional<PlatformObject> existing = objectManager.tree().findByPath(node.path());
            if (existing.isEmpty()) {
                createCount++;
            } else if (FederationProxyMetadata.isProxy(existing.get())) {
                warnings.add("Skipping federation proxy on import: " + node.path());
            } else {
                updateCount++;
            }
        }
        return new ImportPreview(document.nodes().size(), createCount, updateCount, warnings);
    }

    @Transactional
    public ImportResult importSubtree(String json, boolean dryRun) {
        BackupDocument document = parseDocument(json);
        ImportPreview preview = previewImport(json);
        if (dryRun) {
            return ImportResult.dryRun(preview);
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (BackupNode node : document.nodes()) {
            if (node.path().equals(ROOT_PATH)) {
                continue;
            }
            Optional<PlatformObject> existing = objectManager.tree().findByPath(node.path());
            if (existing.isPresent() && FederationProxyMetadata.isProxy(existing.get())) {
                skipped++;
                continue;
            }
            if (existing.isEmpty()) {
                createNode(node);
                created++;
            } else {
                updateNode(node);
                updated++;
            }
        }
        return new ImportResult(preview, created, updated, skipped, false);
    }

    private void createNode(BackupNode node) {
        int lastDot = node.path().lastIndexOf('.');
        if (lastDot <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid node path: " + node.path());
        }
        String parentPath = node.path().substring(0, lastDot);
        String name = node.path().substring(lastDot + 1);
        if (objectManager.tree().findByPath(parentPath).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing parent for import node: " + node.path()
            );
        }
        objectManager.create(
                parentPath,
                name,
                node.type(),
                node.displayName(),
                node.description(),
                node.templateId()
        );
        applyNodeConfig(node);
    }

    private void updateNode(BackupNode node) {
        PlatformObject current = objectManager.require(node.path());
        if (!current.displayName().equals(node.displayName()) || !current.description().equals(node.description())) {
            objectManager.updateInfo(node.path(), node.displayName(), node.description());
        }
        objectManager.reconcileType(node.path(), node.type());
        applyNodeConfig(node);
    }

    private void applyNodeConfig(BackupNode node) {
        PlatformObject platformObject = objectManager.require(node.path());
        for (EventDescriptor event : node.events()) {
            platformObject.addEvent(event);
        }
        for (FunctionDescriptor function : node.functions()) {
            platformObject.addFunction(function);
        }
        for (VariableSnapshot variable : node.variables()) {
            if (FederationProxyMetadata.isFederationVariable(variable.name())) {
                continue;
            }
            objectManager.upsertSystemVariable(
                    node.path(),
                    variable.name(),
                    entityMapper.readSchema(variable.schemaJson()),
                    entityMapper.readDataRecord(variable.valueJson())
            );
        }
        objectManager.persistNodeTree(node.path());
    }

    private Map<String, Object> serializeNode(PlatformObject node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", node.path());
        payload.put("type", node.type().name());
        payload.put("displayName", node.displayName());
        payload.put("description", node.description());
        payload.put("templateId", node.templateId().orElse(null));
        payload.put("appliedModelIds", node.appliedModelIds());
        payload.put("events", node.events().values().toArray(new EventDescriptor[0]));
        payload.put("functions", node.functions().values().toArray(new FunctionDescriptor[0]));
        List<Map<String, Object>> variables = node.variables().values().stream()
                .map(variable -> {
                    var entity = entityMapper.toEntity(node.path(), variable);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", variable.name());
                    row.put("schemaJson", entity.getSchemaJson());
                    row.put("valueJson", entity.getValueJson());
                    row.put("readable", variable.readable());
                    row.put("writable", variable.writable());
                    row.put("historyEnabled", variable.historyEnabled());
                    row.put("historyRetentionDays", variable.historyRetentionDays().orElse(null));
                    return row;
                })
                .toList();
        payload.put("variables", variables);
        return payload;
    }

    private BackupDocument parseDocument(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            int formatVersion = ((Number) root.getOrDefault("formatVersion", 0)).intValue();
            if (formatVersion != FORMAT_VERSION) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported backup format version: " + formatVersion
                );
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) root.get("nodes");
            if (rawNodes == null || rawNodes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup contains no nodes");
            }
            List<BackupNode> nodes = rawNodes.stream().map(this::parseNode).toList();
            return new BackupDocument(nodes);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup JSON: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private BackupNode parseNode(Map<String, Object> raw) {
        String path = String.valueOf(raw.get("path"));
        var type = com.ispf.core.object.ObjectType.valueOf(String.valueOf(raw.get("type")));
        String displayName = String.valueOf(raw.getOrDefault("displayName", path));
        String description = String.valueOf(raw.getOrDefault("description", ""));
        String templateId = raw.get("templateId") != null ? String.valueOf(raw.get("templateId")) : null;
        EventDescriptor[] events = objectMapper.convertValue(
                raw.getOrDefault("events", List.of()),
                EventDescriptor[].class
        );
        FunctionDescriptor[] functions = objectMapper.convertValue(
                raw.getOrDefault("functions", List.of()),
                FunctionDescriptor[].class
        );
        List<Map<String, Object>> rawVariables = (List<Map<String, Object>>) raw.getOrDefault("variables", List.of());
        List<VariableSnapshot> variables = rawVariables.stream()
                .map(row -> new VariableSnapshot(
                        String.valueOf(row.get("name")),
                        String.valueOf(row.get("schemaJson")),
                        row.get("valueJson") != null ? String.valueOf(row.get("valueJson")) : null
                ))
                .toList();
        return new BackupNode(
                path,
                type,
                displayName,
                description,
                templateId,
                List.of(events),
                List.of(functions),
                variables
        );
    }

    private record BackupDocument(List<BackupNode> nodes) {
    }

    private record BackupNode(
            String path,
            com.ispf.core.object.ObjectType type,
            String displayName,
            String description,
            String templateId,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<VariableSnapshot> variables
    ) {
    }

    private record VariableSnapshot(String name, String schemaJson, String valueJson) {
    }

    public record ImportPreview(int nodeCount, int createCount, int updateCount, List<String> warnings) {
    }

    public record ImportResult(
            ImportPreview preview,
            int created,
            int updated,
            int skipped,
            boolean dryRun
    ) {
        static ImportResult dryRun(ImportPreview preview) {
            return new ImportResult(preview, 0, 0, 0, true);
        }
    }
}
