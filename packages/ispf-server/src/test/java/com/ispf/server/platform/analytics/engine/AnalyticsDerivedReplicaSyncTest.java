package com.ispf.server.platform.analytics.engine;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.object.ClusterLiveVariableReplicaPublisher;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.pubsub.VariableChangeInterest;
import com.ispf.server.object.pubsub.VariableChangeSubscriptionRegistry;
import com.ispf.server.workflow.NatsEventBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsDerivedReplicaSyncTest {

    @Mock
    private ClusterProperties clusterProperties;
    @Mock
    private NatsProperties natsProperties;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private NatsEventBridge natsEventBridge;
    @Mock
    private ObjectTree objectTree;
    @Mock
    private PlatformObject node;
    @Mock
    private Variable variable;
    @Mock
    private VariableChangeSubscriptionRegistry variableSubscriptionRegistry;
    @Mock
    private VariableChangeInterest interest;

    private ClusterLiveVariableReplicaPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ClusterLiveVariableReplicaPublisher(
                clusterProperties,
                natsProperties,
                objectManager,
                natsEventBridge,
                variableSubscriptionRegistry
        );
        when(clusterProperties.isLiveVariableSyncActive()).thenReturn(true);
        when(natsProperties.enabled()).thenReturn(true);
        when(natsProperties.replicaEventsEnabled()).thenReturn(true);
        when(variableSubscriptionRegistry.interest(anyString(), anyString())).thenReturn(interest);
    }

    @Test
    void derivedTagConfigWriteFansOutLiveValueSnapshot() {
        when(interest.liveObserver()).thenReturn(true);
        when(clusterProperties.isLiveVariableSyncCoalesceActive()).thenReturn(false);
        String path = "root.platform.devices.tag-a";
        Instant observedAt = Instant.parse("2026-07-09T06:00:00Z");
        DataRecord value = DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", "42")
        );
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(Optional.of(node));
        when(node.getVariable("derivedValue")).thenReturn(Optional.of(variable));
        when(variable.value()).thenReturn(Optional.of(value));

        publisher.onObjectChange(ObjectChangeEvent.variableUpdated(path, "derivedValue", false, true, observedAt));

        verify(natsEventBridge).publishLiveVariableReplicaSync(
                eq(path),
                eq("derivedValue"),
                eq(value),
                eq(observedAt)
        );
    }

    @Test
    void skipsNatsFanOutWhenNoLiveObserver() {
        when(interest.liveObserver()).thenReturn(false);
        String path = "root.platform.devices.tag-a";
        Instant observedAt = Instant.parse("2026-07-09T06:00:00Z");

        publisher.onObjectChange(ObjectChangeEvent.variableUpdated(path, "derivedValue", false, true, observedAt));

        verify(natsEventBridge, never()).publishLiveVariableReplicaSync(
                eq(path),
                eq("derivedValue"),
                org.mockito.ArgumentMatchers.any(),
                eq(observedAt)
        );
    }
}
