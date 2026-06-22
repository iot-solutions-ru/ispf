package com.ispf.server.dashboard;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    private static final DataSchema LAYOUT_SCHEMA = DataSchema.builder("layout")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema TITLE_SCHEMA = DataSchema.builder("title")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema REFRESH_SCHEMA = DataSchema.builder("refreshIntervalMs")
            .field("value", FieldType.INTEGER)
            .build();

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ObjectMapper objectMapper;

    public DashboardService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureDashboardStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        if (node.getVariable("layout").isPresent()) {
            return;
        }
        String templateId = node.templateId().orElse("dashboard-v1");
        resolveModel(templateId).ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    private Optional<ModelDefinition> resolveModel(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return modelRegistry.findByName("dashboard-v1");
        }
        return modelRegistry.findById(templateId)
                .or(() -> modelRegistry.findByName(templateId));
    }

    public DashboardView getDashboard(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        String title = readString(node, "title").orElse(node.displayName());
        int refreshIntervalMs = readInteger(node, "refreshIntervalMs").orElse(5000);
        String layoutJson = readString(node, "layout").orElse(DashboardLayouts.EMPTY_DASHBOARD);
        Object layout = parseLayout(layoutJson);
        return new DashboardView(path, title, refreshIntervalMs, layout, layoutJson);
    }

    @Transactional
    public DashboardView saveLayout(String path, String layoutJson) {
        validateLayoutJson(layoutJson);
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        objectManager.setVariableValue(
                path,
                "layout",
                DataRecord.single(LAYOUT_SCHEMA, Map.of("value", layoutJson))
        );
        return getDashboard(path);
    }

    @Transactional
    public DashboardView updateTitle(String path, String title) {
        objectManager.setVariableValue(
                path,
                "title",
                DataRecord.single(TITLE_SCHEMA, Map.of("value", title))
        );
        return getDashboard(path);
    }

    private void validateLayoutJson(String layoutJson) {
        try {
            objectMapper.readTree(layoutJson);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid layout JSON", e);
        }
    }

    private Object parseLayout(String layoutJson) {
        try {
            return objectMapper.readValue(layoutJson, Object.class);
        } catch (JacksonException e) {
            return Map.of("columns", 12, "rowHeight", 72, "widgets", java.util.List.of());
        }
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    private static Optional<Integer> readInteger(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                    return Integer.parseInt(String.valueOf(value));
                });
    }

    public record DashboardView(
            String path,
            String title,
            int refreshIntervalMs,
            Object layout,
            String layoutJson
    ) {
    }
}
