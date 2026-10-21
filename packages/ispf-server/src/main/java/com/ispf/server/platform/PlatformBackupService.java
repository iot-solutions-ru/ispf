package com.ispf.server.platform;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.plugin.blueprint.SystemIntrinsicBlueprints;
import com.ispf.server.federation.FederationProxyMetadata;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.plugin.blueprint.BlueprintPersistenceService;
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
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PlatformBackupService {

    public static final String ROOT_PATH = "root.platform";
    private static final int FORMAT_VERSION = 1;
    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");

    private final ObjectManager objectManager;
    private final ObjectEntityMapper entityMapper;
    private final ObjectMapper objectMapper;
    private final BlueprintEngine blueprintEngine;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintPersistenceService blueprintPersistence;

    public PlatformBackupService(
            ObjectManager objectManager,
            ObjectEntityMapper entityMapper,
            ObjectMapper objectMapper,
            BlueprintEngine blueprintEngine,
            BlueprintRegistry blueprintRegistry,
            BlueprintPersistenceService blueprintPersistence
    ) {
        this.objectManager = objectManager;
        this.entityMapper = entityMapper;
        this.objectMapper = objectMapper;
        this.blueprintEngine = blueprintEngine;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintPersistence = blueprintPersistence;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportSubtree() {
        return exportSubtree(ROOT_PATH);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportSubtree(String rootPath) {
        String normalizedRoot = normalizeExportRoot(rootPath);
        List<Map<String, Object>> nodes = objectManager.tree().all().stream()
                .filter(node -> node.path().equals(normalizedRoot)
                        || node.path().startsWith(normalizedRoot + "."))
                .sorted(Comparator.comparing(PlatformObject::path))
                .map(this::serializeNode)
                .toList();
        if (nodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No nodes under export root: " + normalizedRoot);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formatVersion", FORMAT_VERSION);
        payload.put("exportedAt", Instant.now().toString());
        payload.put("rootPath", normalizedRoot);
        payload.put("nodeCount", nodes.size());
        payload.put("nodes", nodes);
        return payload;
    }

    static String normalizeExportRoot(String rootPath) {
        String candidate = rootPath == null || rootPath.isBlank() ? ROOT_PATH : rootPath.trim();
        if (!candidate.equals(ROOT_PATH) && !candidate.startsWith(ROOT_PATH + ".")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Export root must be " + ROOT_PATH + " or a descendant path"
            );
        }
        return candidate;
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

    /**
     * Creates one object under {@code parentPath} from a subtree export that contains a single node.
     * The path stored in the file is ignored; {@code name} is the new path segment.
     */
    @Transactional
    public String importNodeAsChild(String parentPath, String name, String json) {
        String parent = normalizeExportRoot(parentPath);
        String segment = name == null ? "" : name.trim();
        if (!PATH_SEGMENT.matcher(segment).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid object name: " + name);
        }
        if (objectManager.tree().findByPath(parent).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing parent: " + parent);
        }
        String childPath = parent + "." + segment;
        if (objectManager.tree().findByPath(childPath).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object already exists: " + childPath);
        }
        BackupDocument document = parseDocument(json);
        if (document.nodes().size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "JSON must contain exactly one exported node"
            );
        }
        BackupNode node = document.nodes().get(0);
        if (ROOT_PATH.equals(node.path())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot import the platform root as an object");
        }
        BlueprintType catalogKind = blueprintKind(parent);
        if (catalogKind != null) {
            return importBlueprint(segment, node, catalogKind);
        }
        objectManager.create(
                parent,
                segment,
                node.type(),
                node.displayName(),
                node.description(),
                node.templateId()
        );
        applyNodeConfig(childPath, node);
        return childPath;
    }

    /**
     * Mixin, instance-type and singleton catalogs open in the blueprint editor.
     * A raw tree node has no row in {@code blueprint_definitions}, so the editor returns 404.
     */
    private String importBlueprint(String name, BackupNode node, BlueprintType kind) {
        BlueprintDefinition model = blueprintFromExport(name, node, kind);
        BlueprintDefinition created;
        try {
            blueprintEngine.releaseOrphanedName(name)
                    .ifPresent(old -> blueprintPersistence.delete(old.id()));
            created = blueprintEngine.createBlueprint(model);
        } catch (BlueprintException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
        blueprintPersistence.persist(created, false);
        objectManager.persistNodeTree(created.catalogObjectPath());
        return created.catalogObjectPath();
    }

    private BlueprintDefinition blueprintFromExport(String name, BackupNode node, BlueprintType kind) {
        Optional<BlueprintDefinition> source = blueprintRegistry.all().stream()
                .filter(model -> node.path().equals(model.catalogObjectPath()))
                .findFirst();
        Instant now = Instant.now();
        if (source.isPresent()) {
            BlueprintDefinition existing = source.get();
            Map<String, String> parameters = new LinkedHashMap<>(existing.parameters());
            parameters.remove("singletonInstancePath");
            parameters.remove(SystemIntrinsicBlueprints.PARAM_SYSTEM_INTRINSIC);
            ObjectType target = blueprintTarget(existing.targetObjectType());
            return new BlueprintDefinition(
                    UUID.randomUUID().toString(),
                    name,
                    node.description() != null && !node.description().isBlank()
                            ? node.description()
                            : existing.description(),
                    kind,
                    target,
                    existing.suitabilityExpression(),
                    existing.variables(),
                    existing.events(),
                    existing.functions(),
                    existing.bindingRules(),
                    existing.sqlBindings(),
                    existing.alertRules(),
                    parameters,
                    now,
                    now
            );
        }
        ObjectType target = blueprintTarget(node.type());
        return new BlueprintDefinition(
                UUID.randomUUID().toString(),
                name,
                node.description(),
                kind,
                target,
                suitabilityFrom(node),
                variablesFrom(node),
                node.events(),
                node.functions(),
                List.of(),
                Map.of(),
                now,
                now
        );
    }

    private List<BlueprintVariableDefinition> variablesFrom(BackupNode node) {
        List<BlueprintVariableDefinition> variables = new ArrayList<>();
        for (VariableSnapshot variable : node.variables()) {
            if (variable.name() == null
                    || variable.name().isBlank()
                    || "blueprintType".equals(variable.name())
                    || "suitabilityExpression".equals(variable.name())
                    || BindingRulesConstants.isReservedVariable(variable.name())
                    || FederationProxyMetadata.isFederationVariable(variable.name())
                    || variable.schemaJson() == null
                    || variable.schemaJson().isBlank()
                    || "null".equals(variable.schemaJson())) {
                continue;
            }
            DataSchema schema = entityMapper.readSchema(variable.schemaJson());
            DataRecord value = variable.valueJson() != null && !variable.valueJson().isBlank()
                    && !"null".equals(variable.valueJson())
                    ? entityMapper.readDataRecord(variable.valueJson())
                    : null;
            variables.add(BlueprintVariableDefinition.of(
                    variable.name(),
                    "",
                    "default",
                    schema,
                    true,
                    true,
                    value
            ));
        }
        return variables;
    }

    private String suitabilityFrom(BackupNode node) {
        for (VariableSnapshot variable : node.variables()) {
            if (!"suitabilityExpression".equals(variable.name()) || variable.valueJson() == null) {
                continue;
            }
            DataRecord record = entityMapper.readDataRecord(variable.valueJson());
            Object value = record.get("value", 0);
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    /**
     * Mixin, instance-type, and singleton catalogs accept the same blueprint targets as the create dialog.
     * A migration or other catalog object becomes {@link ObjectType#CUSTOM}.
     */
    private static ObjectType blueprintTarget(ObjectType sourceType) {
        if (sourceType == ObjectType.CUSTOM || sourceType == ObjectType.DEVICE) {
            return sourceType;
        }
        return ObjectType.CUSTOM;
    }

    private static BlueprintType blueprintKind(String parentPath) {
        if (BlueprintCatalogRoots.MIXIN.equals(parentPath)) {
            return BlueprintType.MIXIN;
        }
        if (BlueprintCatalogRoots.INSTANCE.equals(parentPath)) {
            return BlueprintType.INSTANCE;
        }
        if (BlueprintCatalogRoots.SINGLETON.equals(parentPath)) {
            return BlueprintType.SINGLETON;
        }
        return null;
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
        applyNodeConfig(node.path(), node);
    }

    private void updateNode(BackupNode node) {
        PlatformObject current = objectManager.require(node.path());
        if (!current.displayName().equals(node.displayName()) || !current.description().equals(node.description())) {
            objectManager.updateInfo(node.path(), node.displayName(), node.description());
        }
        objectManager.reconcileType(node.path(), node.type());
        applyNodeConfig(node.path(), node);
    }

    private void applyNodeConfig(String path, BackupNode node) {
        PlatformObject platformObject = objectManager.require(path);
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
                    path,
                    variable.name(),
                    entityMapper.readSchema(variable.schemaJson()),
                    entityMapper.readDataRecord(variable.valueJson())
            );
        }
        objectManager.persistNodeTree(path);
    }

    private Map<String, Object> serializeNode(PlatformObject node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", node.path());
        payload.put("type", node.type().name());
        payload.put("displayName", node.displayName());
        payload.put("description", node.description());
        payload.put("templateId", node.templateId().orElse(null));
        payload.put("appliedBlueprintIds", node.appliedBlueprintIds());
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
