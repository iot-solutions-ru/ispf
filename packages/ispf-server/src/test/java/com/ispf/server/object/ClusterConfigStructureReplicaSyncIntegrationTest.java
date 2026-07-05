package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import com.ispf.core.object.ObjectNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0030: structure/config replica sync via PG reload on NATS ingress events (BL-143).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.cluster.enabled=true")
class ClusterConfigStructureReplicaSyncIntegrationTest {

    private static final String PARENT = "root.platform.devices";
    private static final String NAME = "cluster-config-sync-test";
    private static final String PATH = PARENT + "." + NAME;
    private static final DataSchema DIAGRAM = DataSchema.builder("diagram")
            .field("elements", FieldType.STRING)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ClusterObjectTreeReplicaSync replicaSync;

    @Autowired
    private ObjectNodeRepository nodeRepository;

    @Autowired
    private ObjectVariableRepository variableRepository;

    @AfterEach
    void cleanup() {
        objectManager.tree().findByPath(PATH).ifPresent(node -> objectManager.delete(PATH));
    }

    @Test
    void createUpdateDeleteRoundTripViaReplicaSync() {
        objectManager.create(PARENT, NAME, ObjectType.DEVICE, "Cluster sync device", null, null);
        objectManager.persistNodeTree(PATH);
        objectManager.removePathFromMemoryIfPresent(PATH);
        assertThat(objectManager.tree().findByPath(PATH)).isEmpty();

        replicaSync.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.CREATED, PATH));
        assertThat(objectManager.tree().findByPath(PATH)).isPresent();
        assertThat(objectManager.require(PATH).displayName()).isEqualTo("Cluster sync device");

        objectManager.require(PATH).updateInfo("Renamed on writer", null);
        objectManager.persistNodeTree(PATH);
        objectManager.require(PATH).updateInfo("Stale follower RAM", null);

        replicaSync.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.UPDATED, PATH));
        assertThat(objectManager.require(PATH).displayName()).isEqualTo("Renamed on writer");

        objectManager.createVariable(
                PATH,
                "diagram",
                DIAGRAM,
                true,
                true,
                DataRecord.single(DIAGRAM, Map.of("elements", "[{\"id\":\"m1\"},{\"id\":\"m2\"}]")),
                false,
                null
        );
        objectManager.setVariableValue(
                PATH,
                "diagram",
                DataRecord.single(DIAGRAM, Map.of("elements", "[{\"id\":\"m1\"},{\"id\":\"m2\"}]"))
        );
        objectManager.require(PATH).removeVariable("diagram");

        replicaSync.onObjectChange(ObjectChangeEvent.variableUpdated(PATH, "diagram", 2L, "admin"));
        assertThat(objectManager.require(PATH).getVariable("diagram")).isPresent();

        objectManager.removePathFromMemoryIfPresent(PATH);
        assertThat(objectManager.tree().findByPath(PATH)).isEmpty();
        objectManager.delete(PATH);
        assertThat(nodeRepository.findByPath(PATH)).isEmpty();

        replicaSync.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.DELETED, PATH));
        assertThat(objectManager.tree().findByPath(PATH)).isEmpty();
    }

    @Test
    void requireEvictsStaleRamWhenNodeAbsentFromDatabase() {
        objectManager.create(PARENT, NAME, ObjectType.DEVICE, "Ghost test", null, null);
        objectManager.persistNodeTree(PATH);
        ObjectNodeEntity entity = nodeRepository.findByPath(PATH).orElseThrow();
        variableRepository.deleteByObjectPath(PATH);
        nodeRepository.deleteById(entity.getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> objectManager.require(PATH))
                .isInstanceOf(ObjectNotFoundException.class);
        assertThat(objectManager.tree().findByPath(PATH)).isEmpty();
    }
}
