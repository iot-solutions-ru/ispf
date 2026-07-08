package com.ispf.server.platform.analytics;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AssetAnalyticsService {

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;

    public AssetAnalyticsService(ObjectManager objectManager, SystemObjectStructureService structureService) {
        this.objectManager = objectManager;
        this.structureService = structureService;
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

    @Transactional(readOnly = true)
    public List<AnalyticsTemplate> listTemplates() {
        ensureCatalog();
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
}
