package com.ispf.server.platform;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@SpringBootTest
@ActiveProfiles("test")
class PlatformBackupScopedExportTest {

    @Autowired
    private PlatformBackupService platformBackupService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BlueprintEngine blueprintEngine;

    @Autowired
    private BlueprintRegistry blueprintRegistry;

    @Autowired
    private BlueprintPersistenceService blueprintPersistence;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exportsScopedSubtreeUnderRootPlatform() {
        var payload = platformBackupService.exportSubtree("root.platform.devices");
        assertEquals("root.platform.devices", payload.get("rootPath"));
        assertTrue(((Number) payload.get("nodeCount")).intValue() >= 1);
    }

    @Test
    void rejectsExportOutsideRootPlatform() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> platformBackupService.exportSubtree("root.other")
        );
        assertEquals(BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void importsOneExportedNodeUnderANewName() throws Exception {
        String source = "root.platform.devices.json-import-src";
        String copy = "root.platform.devices.json-import-copy";
        deleteIfPresent(copy);
        deleteIfPresent(source);
        try {
            objectManager.create(
                    "root.platform.devices",
                    "json-import-src",
                    ObjectType.DEVICE,
                    "Source meter",
                    "copied later",
                    null
            );
            DataSchema schema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
            objectManager.upsertSystemVariable(
                    source,
                    "reading",
                    schema,
                    DataRecord.single(schema, Map.of("value", 3.5))
            );
            String json = objectMapper.writeValueAsString(platformBackupService.exportSubtree(source));
            assertEquals(
                    copy,
                    platformBackupService.importNodeAsChild("root.platform.devices", "json-import-copy", json)
            );
            var imported = objectManager.require(copy);
            assertEquals(ObjectType.DEVICE, imported.type());
            assertEquals("Source meter", imported.displayName());
            assertEquals("copied later", imported.description());
            assertTrue(imported.variables().containsKey("reading"));
        } finally {
            deleteIfPresent(copy);
            deleteIfPresent(source);
        }
    }

    @Test
    void rejectsImportWhenTheFileContainsMoreThanOneNode() {
        String json = """
                {
                  "formatVersion": 1,
                  "nodes": [
                    {"path":"root.platform.devices.a","type":"DEVICE","displayName":"A","description":""},
                    {"path":"root.platform.devices.b","type":"DEVICE","displayName":"B","description":""}
                  ]
                }
                """;
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> platformBackupService.importNodeAsChild("root.platform.devices", "too-many", json)
        );
        assertEquals(BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void importsOneNodeIntoAnInstanceCatalogAsABlueprint() throws Exception {
        String sourceName = "json-bp-src";
        String copyName = "json-bp-copy";
        deleteBlueprint(sourceName);
        deleteBlueprint(copyName);
        DataSchema schema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        var source = new BlueprintDefinition(
                java.util.UUID.randomUUID().toString(),
                sourceName,
                "meter type",
                BlueprintType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(BlueprintVariableDefinition.of(
                        "reading",
                        "",
                        "default",
                        schema,
                        true,
                        true,
                        DataRecord.single(schema, Map.of("value", 1.0))
                )),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        try {
            var created = blueprintEngine.createBlueprint(source);
            blueprintPersistence.persist(created, false);
            objectManager.persistNodeTree(created.catalogObjectPath());
            String json = objectMapper.writeValueAsString(
                    platformBackupService.exportSubtree(created.catalogObjectPath())
            );
            String copyPath = platformBackupService.importNodeAsChild(
                    "root.platform.instance-types",
                    copyName,
                    json
            );
            assertEquals("root.platform.instance-types." + copyName, copyPath);
            var copy = blueprintRegistry.findByName(copyName).orElseThrow();
            assertEquals(BlueprintType.INSTANCE, copy.type());
            assertEquals(ObjectType.DEVICE, copy.targetObjectType());
            assertTrue(copy.variables().stream().anyMatch(variable -> "reading".equals(variable.name())));
            assertEquals(ObjectType.BLUEPRINT, objectManager.require(copyPath).type());
        } finally {
            deleteBlueprint(copyName);
            deleteBlueprint(sourceName);
        }
    }

    @Test
    void importIntoAnInstanceCatalogKeepsDeviceTargetAndReplacesOtherTypesWithCustom() {
        String deviceName = "json-bp-device";
        String migrationName = "json-bp-migration";
        deleteBlueprint(deviceName);
        deleteBlueprint(migrationName);
        String deviceJson = """
                {
                  "formatVersion": 1,
                  "nodes": [
                    {"path":"root.platform.devices.meter","type":"DEVICE","displayName":"Meter","description":""}
                  ]
                }
                """;
        String migrationJson = """
                {
                  "formatVersion": 1,
                  "nodes": [
                    {"path":"root.platform.migrations.cutoff_schema_v11","type":"MIGRATION","displayName":"Cutoff","description":""}
                  ]
                }
                """;
        try {
            platformBackupService.importNodeAsChild("root.platform.instance-types", deviceName, deviceJson);
            platformBackupService.importNodeAsChild("root.platform.instance-types", migrationName, migrationJson);
            assertEquals(ObjectType.DEVICE, blueprintRegistry.findByName(deviceName).orElseThrow().targetObjectType());
            assertEquals(BlueprintType.INSTANCE, blueprintRegistry.findByName(migrationName).orElseThrow().type());
            assertEquals(ObjectType.CUSTOM, blueprintRegistry.findByName(migrationName).orElseThrow().targetObjectType());
        } finally {
            deleteBlueprint(deviceName);
            deleteBlueprint(migrationName);
        }
    }

    private void deleteBlueprint(String name) {
        blueprintRegistry.findByName(name).ifPresent(model -> {
            blueprintEngine.deleteBlueprint(model.id());
            blueprintPersistence.delete(model.id());
            deleteIfPresent(model.catalogObjectPath());
        });
        deleteIfPresent("root.platform.instance-types." + name);
    }

    private void deleteIfPresent(String path) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.delete(path);
        }
    }
}
