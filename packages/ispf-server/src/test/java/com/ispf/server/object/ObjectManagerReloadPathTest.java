package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ObjectManagerReloadPathTest {

    private static final String PATH = "root.platform.devices.demo-sensor-01";
    private static final DataSchema DIAGRAM = DataSchema.builder("diagram")
            .field("elements", FieldType.STRING)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @AfterEach
    void restoreDisplayName() {
        objectManager.tree().findByPath(PATH).ifPresent(device -> {
            if (!"Demo Sensor 01".equals(device.displayName())) {
                device.updateInfo("Demo Sensor 01", null);
                objectManager.persistNodeTree(PATH);
            }
        });
        objectManager.tree().findByPath(PATH).ifPresent(device -> device.removeVariable("diagram"));
    }

    @Test
    void reloadPathUpdatesExistingNodeMetadataFromDatabase() {
        objectManager.require(PATH).updateInfo("Stale RAM label", null);
        assertThat(objectManager.require(PATH).displayName()).isEqualTo("Stale RAM label");

        objectManager.reloadPathFromDatabase(PATH);

        assertThat(objectManager.require(PATH).displayName()).isEqualTo("Demo Sensor 01");
    }

    @Test
    void reloadPathUpsertsAndRemovesVariablesFromDatabase() {
        objectManager.createVariable(
                PATH,
                "diagram",
                DIAGRAM,
                true,
                true,
                DataRecord.single(DIAGRAM, Map.of("elements", "[{\"id\":\"t1\"}]")),
                false,
                null
        );
        objectManager.setVariableValue(
                PATH,
                "diagram",
                DataRecord.single(DIAGRAM, Map.of("elements", "[{\"id\":\"t1\"}]"))
        );
        objectManager.require(PATH).removeVariable("diagram");
        assertThat(objectManager.require(PATH).getVariable("diagram")).isEmpty();

        objectManager.reloadPathFromDatabase(PATH);

        assertThat(objectManager.require(PATH).getVariable("diagram")).isPresent();
        assertThat(objectManager.require(PATH).getVariable("diagram").orElseThrow().value()).isPresent();
    }
}
