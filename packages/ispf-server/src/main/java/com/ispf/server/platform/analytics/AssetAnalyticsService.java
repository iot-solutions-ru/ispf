package com.ispf.server.platform.analytics;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineScheduler;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AssetAnalyticsService {

    public static final Set<String> BUILTIN_TEMPLATE_IDS = Set.of("rollingAvg", "rateOfChange", "oee");

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;
    private final BlueprintApplicationService blueprintApplicationService;
    private final BlueprintRegistry blueprintRegistry;
    private final AnalyticsDerivedTagService derivedTagService;
    private final AnalyticsEngineScheduler engineScheduler;
    private final AnalyticsTagMetadataService tagMetadataService;

    public AssetAnalyticsService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService,
            BlueprintApplicationService blueprintApplicationService,
            BlueprintRegistry blueprintRegistry,
            AnalyticsDerivedTagService derivedTagService,
            AnalyticsEngineScheduler engineScheduler,
            AnalyticsTagMetadataService tagMetadataService
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
        this.blueprintApplicationService = blueprintApplicationService;
        this.blueprintRegistry = blueprintRegistry;
        this.derivedTagService = derivedTagService;
        this.engineScheduler = engineScheduler;
        this.tagMetadataService = tagMetadataService;
    }

    @Transactional
    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(
                objectManager,
                AssetAnalyticsPaths.ANALYTICS_ROOT,
                ObjectType.ANALYTICS,
                null
        );
        ensureBuiltInTemplates();
    }

    /** Read-only listing; catalog ensured by {@link #ensureCatalog()} at bootstrap. */
    @Transactional(readOnly = true)
    public List<AnalyticsTemplate> listTemplates() {
        List<AnalyticsTemplate> templates = new ArrayList<>();
        if (objectManager.tree().findByPath(AssetAnalyticsPaths.ANALYTICS_ROOT).isEmpty()) {
            return templates;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(AssetAnalyticsPaths.ANALYTICS_ROOT)) {
            if (child.type() != ObjectType.ANALYTICS_TEMPLATE) {
                continue;
            }
            toTemplate(child.path(), child).ifPresent(templates::add);
        }
        return templates;
    }

    @Transactional(readOnly = true)
    public AnalyticsTemplate getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.ANALYTICS_TEMPLATE) {
            throw new IllegalArgumentException("Not an analytics template: " + path);
        }
        return toTemplate(path, node).orElseThrow(() -> new IllegalArgumentException("Invalid analytics template: " + path));
    }

    @Transactional
    public AnalyticsTemplate createTemplate(AnalyticsTemplate definition) {
        if (definition.templateId() == null || definition.templateId().isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        if (BUILTIN_TEMPLATE_IDS.contains(definition.templateId())) {
            throw new IllegalArgumentException("Reserved templateId: " + definition.templateId());
        }
        ensureCatalog();
        String path = pathForTemplateId(definition.templateId());
        if (objectManager.tree().findByPath(path).isPresent()) {
            throw new IllegalArgumentException("Template already exists: " + definition.templateId());
        }
        objectManager.create(
                AssetAnalyticsPaths.ANALYTICS_ROOT,
                sanitizeNodeName(definition.templateId()),
                ObjectType.ANALYTICS_TEMPLATE,
                blankToDefault(definition.displayName(), definition.templateId()),
                definition.description() != null ? definition.description() : "",
                null
        );
        String windowBucket = blankToDefault(definition.windowBucket(), "5m");
        validateWindowBucket(windowBucket);
        writeTemplateVariables(
                path,
                definition.templateId(),
                blankToDefault(definition.helper(), "rollingAvg"),
                blankToDefault(definition.blueprintName(), AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL),
                windowBucket
        );
        applyTemplateFields(path, definition);
        if (definition.displayName() != null && !definition.displayName().isBlank()) {
            objectManager.updateInfo(path, definition.displayName(), definition.description());
        }
        return getByPath(path);
    }

    @Transactional
    public AnalyticsTemplate updateTemplate(String path, AnalyticsTemplate patch) {
        AnalyticsTemplate existing = getByPath(path);
        boolean builtIn = BUILTIN_TEMPLATE_IDS.contains(existing.templateId());
        String templateId = builtIn ? existing.templateId() : blankToDefault(patch.templateId(), existing.templateId());
        String helper = builtIn ? existing.helper() : blankToDefault(patch.helper(), existing.helper());
        String blueprintName = builtIn
                ? existing.blueprintName()
                : blankToDefault(patch.blueprintName(), existing.blueprintName());
        validateHelperAndBlueprint(helper, blueprintName);
        String windowBucket = blankToDefault(patch.windowBucket(), existing.windowBucket());
        validateWindowBucket(windowBucket);
        writeTemplateVariables(path, templateId, helper, blueprintName, windowBucket);
        applyTemplateFields(path, new AnalyticsTemplate(
                path,
                templateId,
                patch.displayName() != null ? patch.displayName() : existing.displayName(),
                patch.description() != null ? patch.description() : existing.description(),
                helper,
                patch.sourcePath() != null ? patch.sourcePath() : existing.sourcePath(),
                patch.sourceVariable() != null ? patch.sourceVariable() : existing.sourceVariable(),
                patch.sourceField() != null ? patch.sourceField() : existing.sourceField(),
                blankToDefault(patch.windowBucket(), existing.windowBucket()),
                blueprintName,
                patch.enabled()
        ));
        if (patch.displayName() != null || patch.description() != null) {
            objectManager.updateInfo(
                    path,
                    patch.displayName() != null ? patch.displayName() : existing.displayName(),
                    patch.description() != null ? patch.description() : existing.description()
            );
        }
        return getByPath(path);
    }

    @Transactional
    public void deleteTemplate(String path) {
        AnalyticsTemplate existing = getByPath(path);
        if (BUILTIN_TEMPLATE_IDS.contains(existing.templateId())) {
            throw new IllegalArgumentException("Built-in template cannot be deleted: " + existing.templateId());
        }
        objectManager.delete(path);
    }

    @Transactional
    public ApplyTemplateResult applyTemplateToDevice(ApplyTemplateCommand command) {
        AnalyticsTemplate template = getByPath(command.templatePath());
        if (!template.enabled()) {
            throw new IllegalArgumentException("Template is disabled: " + template.templateId());
        }
        PlatformObject device = objectManager.require(command.devicePath());
        if (device.type() != ObjectType.DEVICE) {
            throw new IllegalArgumentException("Target is not a device: " + command.devicePath());
        }
        BlueprintDefinition model = blueprintRegistry.findByName(template.blueprintName())
                .orElseThrow(() -> new IllegalArgumentException("Blueprint not found: " + template.blueprintName()));
        blueprintApplicationService.applyBlueprintWithRules(model, command.devicePath(), Map.of());

        String sourcePath = firstNonBlank(command.sourcePath(), template.sourcePath(), command.devicePath());
        String sourceVariable = requireNonBlank(command.sourceVariable(), "sourceVariable");
        String sourceField = firstNonBlank(command.sourceField(), template.sourceField(), "value");
        String windowBucket = firstNonBlank(command.windowBucket(), template.windowBucket(), "5m");
        validateWindowBucket(windowBucket);

        setString(command.devicePath(), "sourcePath", sourcePath);
        setString(command.devicePath(), "sourceVariable", sourceVariable);
        setString(command.devicePath(), "sourceField", sourceField);
        setString(command.devicePath(), "windowBucket", windowBucket);

        if (AnalyticsBlueprintBootstrap.OEE_MODEL.equals(template.blueprintName())) {
            setString(command.devicePath(), "availabilityVariable",
                    requireNonBlank(command.availabilityVariable(), "availabilityVariable"));
            setString(command.devicePath(), "performanceVariable",
                    requireNonBlank(command.performanceVariable(), "performanceVariable"));
            setString(command.devicePath(), "qualityVariable",
                    requireNonBlank(command.qualityVariable(), "qualityVariable"));
        }

        AnalyticsDerivedTagService.DerivedTagRefreshResult refresh =
                derivedTagService.refreshDevice(command.devicePath());
        engineScheduler.syncSchedules();
        return new ApplyTemplateResult(command.devicePath(), template.templateId(), template.blueprintName(), refresh);
    }

    private void applyTemplateFields(String path, AnalyticsTemplate definition) {
        setString(path, "sourcePath", definition.sourcePath() != null ? definition.sourcePath() : "");
        setString(path, "sourceVariable", definition.sourceVariable() != null ? definition.sourceVariable() : "");
        setString(path, "sourceField", blankToDefault(definition.sourceField(), "value"));
        setBoolean(path, "enabled", definition.enabled());
    }

    private static void validateHelperAndBlueprint(String helper, String blueprintName) {
        if (helper.isBlank()) {
            throw new IllegalArgumentException("helper is required");
        }
        if (blueprintName.isBlank()) {
            throw new IllegalArgumentException("blueprintName is required");
        }
    }

    static void validateWindowBucket(String windowBucket) {
        VariableHistoryService.parseBucket(windowBucket);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    @Transactional
    public void ensureBuiltInTemplates() {
        upsertTemplate(
                "rollingAvg",
                "Rolling average",
                "Historian aggregate avg over windowBucket — AF-like lite derived tag",
                "rollingAvg",
                AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL,
                "5m"
        );
        upsertTemplate(
                "rateOfChange",
                "Rate of change",
                "Delta per historian bucket window — AF-like lite derived tag",
                "rateOfChange",
                AnalyticsBlueprintBootstrap.RATE_OF_CHANGE_MODEL,
                "1h"
        );
        upsertTemplate(
                "oee",
                "OEE composite",
                "Availability × Performance × Quality over shift window — aligns with mes_oee_getKpi",
                "oee",
                AnalyticsBlueprintBootstrap.OEE_MODEL,
                "8h"
        );
    }

    private void upsertTemplate(
            String templateId,
            String displayName,
            String description,
            String helper,
            String blueprintName,
            String windowBucket
    ) {
        String path = pathForTemplateId(templateId);
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    AssetAnalyticsPaths.ANALYTICS_ROOT,
                    sanitizeNodeName(templateId),
                    ObjectType.ANALYTICS_TEMPLATE,
                    displayName,
                    description,
                    null
            );
        } else {
            objectManager.updateInfo(path, displayName, description);
        }
        writeTemplateVariables(path, templateId, helper, blueprintName, windowBucket);
    }

    private void writeTemplateVariables(
            String path,
            String templateId,
            String helper,
            String blueprintName,
            String windowBucket
    ) {
        structureService.ensureAnalyticsTemplateStructure(path);
        setString(path, "templateId", templateId);
        setString(path, "helper", helper);
        setString(path, "sourcePath", "");
        setString(path, "sourceVariable", "");
        setString(path, "sourceField", "value");
        setString(path, "windowBucket", windowBucket);
        setString(path, "blueprintName", blueprintName);
        setBoolean(path, "enabled", true);
    }

    public String pathForTemplateId(String templateId) {
        return AssetAnalyticsPaths.ANALYTICS_ROOT + "." + sanitizeNodeName(templateId);
    }

    public static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "template";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "template";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "t_" + sanitized;
        }
        return sanitized;
    }

    private Optional<AnalyticsTemplate> toTemplate(String path, PlatformObject node) {
        String templateId = readString(node, "templateId").orElse(path.substring(path.lastIndexOf('.') + 1));
        return Optional.of(new AnalyticsTemplate(
                path,
                templateId,
                node.displayName(),
                node.description(),
                readString(node, "helper").orElse(""),
                readString(node, "sourcePath").orElse(""),
                readString(node, "sourceVariable").orElse(""),
                readString(node, "sourceField").orElse("value"),
                readString(node, "windowBucket").orElse("5m"),
                readString(node, "blueprintName").orElse(""),
                readBoolean(node, "enabled").orElse(true)
        ));
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : "")));
    }

    private void setBoolean(String path, String variable, boolean value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(BOOLEAN_SCHEMA, Map.of("value", value)));
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> String.valueOf(r.firstRow().get("value")));
    }

    private static Optional<Boolean> readBoolean(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> {
            Object v = r.firstRow().get("value");
            return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
        });
    }

    public record AnalyticsTemplate(
            String path,
            String templateId,
            String displayName,
            String description,
            String helper,
            String sourcePath,
            String sourceVariable,
            String sourceField,
            String windowBucket,
            String blueprintName,
            boolean enabled
    ) {
    }

    public record ApplyTemplateCommand(
            String templatePath,
            String devicePath,
            String sourcePath,
            String sourceVariable,
            String sourceField,
            String windowBucket,
            String availabilityVariable,
            String performanceVariable,
            String qualityVariable
    ) {
    }

    public record ApplyTemplateResult(
            String devicePath,
            String templateId,
            String blueprintName,
            AnalyticsDerivedTagService.DerivedTagRefreshResult refresh
    ) {
    }
}
