package com.ispf.server.object;

import com.ispf.server.config.ClusterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterObjectTreeReplicaSyncTest {

    private static final String PATH = "root.platform.mimics.test";

    @Mock
    private ClusterProperties clusterProperties;

    @Mock
    private ObjectManager objectManager;

    private ClusterObjectTreeReplicaSync sync;

    @BeforeEach
    void setUp() {
        sync = new ClusterObjectTreeReplicaSync(clusterProperties, objectManager);
        when(clusterProperties.enabled()).thenReturn(true);
    }

    @Test
    void reloadsStructureFromDatabaseOnPeerCreateOrUpdate() {
        when(objectManager.isInitialized()).thenReturn(true);
        ObjectChangeEvent created = ObjectChangeEvent.of(ObjectChangeType.CREATED, PATH);
        ObjectChangeEvent updated = ObjectChangeEvent.of(ObjectChangeType.UPDATED, PATH);

        sync.onObjectChange(created);
        sync.onObjectChange(updated);

        verify(objectManager, times(2)).reloadPathFromDatabase(PATH);
    }

    @Test
    void removesDeletedPathFromMemoryOnPeerDelete() {
        when(objectManager.isInitialized()).thenReturn(true);
        ObjectChangeEvent deleted = ObjectChangeEvent.of(ObjectChangeType.DELETED, PATH);

        sync.onObjectChange(deleted);

        verify(objectManager).removePathFromMemoryIfPresent(PATH);
    }

    @Test
    void reloadsConfigVariableFromDatabaseOnPeerUpdate() {
        when(objectManager.isInitialized()).thenReturn(true);
        ObjectChangeEvent event = ObjectChangeEvent.variableUpdated(PATH, "diagram", 2L, "admin");

        sync.onObjectChange(event);

        verify(objectManager).syncVariableFromDatabase(PATH, "diagram");
    }

    @Test
    void skipsTelemetryAndReplicaIngressEvents() {
        sync.onObjectChange(ObjectChangeEvent.variableUpdated(PATH, "temperature", true));
        sync.onObjectChange(ObjectChangeEvent.variableUpdatedReplicaIngress(PATH, "temperature", null));

        verify(objectManager, never()).syncVariableFromDatabase(PATH, "temperature");
    }
}
