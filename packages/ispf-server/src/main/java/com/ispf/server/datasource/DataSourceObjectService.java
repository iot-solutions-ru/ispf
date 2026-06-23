package com.ispf.server.datasource;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DataSourceObjectService {

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;

    public DataSourceObjectService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
    }

    @Transactional
    public void ensureCatalog() {
        if (objectManager.tree().findByPath(DataSourcePathResolver.DATA_SOURCES_ROOT).isEmpty()) {
            objectManager.create(
                    "root.platform",
                    "data-sources",
                    ObjectType.DATA_SOURCES,
                    "Data Sources",
                    "SQL schema references for reports, bindings, and script functions",
                    null
            );
        } else {
            objectManager.reconcileType(DataSourcePathResolver.DATA_SOURCES_ROOT, ObjectType.DATA_SOURCES);
        }
    }

    @Transactional
    public void ensureDataSource(String nodeName, String displayName, String schemaName, String description) {
        ensureCatalog();
        String path = DataSourcePathResolver.DATA_SOURCES_ROOT + "." + DataSourcePathResolver.sanitizeNodeName(nodeName);
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    DataSourcePathResolver.DATA_SOURCES_ROOT,
                    DataSourcePathResolver.sanitizeNodeName(nodeName),
                    ObjectType.DATA_SOURCE,
                    displayName != null ? displayName : nodeName,
                    description != null ? description : "",
                    "data-source-v1"
            );
        } else {
            objectManager.updateInfo(path, displayName != null ? displayName : nodeName, description != null ? description : "");
            objectManager.reconcileType(path, ObjectType.DATA_SOURCE);
        }
        ensureStructure(path);
        setString(path, "schemaName", schemaName);
        if (displayName != null) {
            setString(path, "displayName", displayName);
        }
    }

    @Transactional
    public void ensureStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        if (node.getVariable("schemaName").isPresent()) {
            return;
        }
        modelRegistry.findByName("data-source-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    public String pathForNodeName(String nodeName) {
        return DataSourcePathResolver.DATA_SOURCES_ROOT + "." + DataSourcePathResolver.sanitizeNodeName(nodeName);
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(STRING_SCHEMA, java.util.Map.of("value", value != null ? value : ""))
        );
    }

    public Optional<String> readSchemaName(String path) {
        return objectManager.tree().findByPath(path)
                .flatMap(node -> node.getVariable("schemaName"))
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(s -> !s.isBlank());
    }

    @Transactional(readOnly = true)
    public DataSourceView getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        ensureStructure(path);
        return new DataSourceView(
                path,
                node.displayName(),
                node.description(),
                readString(node, "displayName").orElse(node.displayName()),
                readSchemaName(path).orElse("")
        );
    }

    @Transactional
    public DataSourceView create(String name, String displayName, String schemaName, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("schemaName is required");
        }
        ensureDataSource(name, displayName, schemaName, description);
        return getByPath(pathForNodeName(name));
    }

    @Transactional
    public DataSourceView update(String path, String displayName, String schemaName, String description) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        String nextDisplayName = displayName != null && !displayName.isBlank() ? displayName : node.displayName();
        String nextDescription = description != null ? description : node.description();
        objectManager.updateInfo(path, nextDisplayName, nextDescription);
        if (schemaName != null) {
            setString(path, "schemaName", schemaName);
        }
        if (displayName != null) {
            setString(path, "displayName", displayName);
        }
        return getByPath(path);
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(Variable::value)
                .map(record -> String.valueOf(record.firstRow().get("value")));
    }

    public record DataSourceView(
            String path,
            String displayName,
            String description,
            String variableDisplayName,
            String schemaName
    ) {
    }
}
