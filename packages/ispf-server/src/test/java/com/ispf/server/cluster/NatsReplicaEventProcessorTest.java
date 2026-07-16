package com.ispf.server.cluster;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.object.ClusterStructureReplicaApplier;
import com.ispf.server.object.ClusterVariableReplicaApplier;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.pubsub.VariableChangeInterest;
import com.ispf.server.object.pubsub.VariableChangeSubscriptionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NatsReplicaEventProcessorTest {

    @Mock
    private ClusterVariableReplicaApplier replicaApplier;
    @Mock
    private ClusterStructureReplicaApplier structureReplicaApplier;
    @Mock
    private VariableChangeSubscriptionRegistry variableSubscriptionRegistry;
    @Mock
    private VariableChangeInterest interest;

    private NatsReplicaEventProcessor processor;

    @AfterEach
    void shutdown() {
        if (processor != null) {
            processor.shutdown();
        }
    }

    @Test
    void appliesLiveVariableSnapshotFromPeerReplica() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        processor = newProcessor("replica-2", objectMapper);
        DataRecord value = DataRecord.single(
                DataSchema.builder("telemetry").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.5)
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "VARIABLE_UPDATED");
        body.put("path", "root.platform.devices.d1");
        body.put("variableName", "temperature");
        body.put("source", "replica-1");
        body.put("value", value);
        body.put("observedAt", "2026-07-10T12:00:00Z");
        processor.processPayload(objectMapper.writeValueAsBytes(body));

        verify(replicaApplier).apply(
                eq("root.platform.devices.d1"),
                eq("temperature"),
                any(DataRecord.class),
                eq(Instant.parse("2026-07-10T12:00:00Z"))
        );
        verify(structureReplicaApplier, never()).apply(any(), anyString(), any());
    }

    @Test
    void ignoresEventsFromSameReplica() throws Exception {
        processor = newProcessor("replica-1");
        String json = """
                {
                  "type": "CREATED",
                  "path": "root.platform.devices.d2",
                  "source": "replica-1"
                }
                """;
        processor.processPayload(json.getBytes(StandardCharsets.UTF_8));

        verify(structureReplicaApplier, never()).apply(any(), anyString(), any());
        verify(replicaApplier, never()).apply(any(), any(), any(), any());
    }

    @Test
    void appliesStructuralReplicaEventViaApplier() throws Exception {
        processor = newProcessor("replica-2");
        String json = """
                {
                  "type": "CREATED",
                  "path": "root.platform.devices.d2",
                  "source": "replica-1"
                }
                """;
        processor.processPayload(json.getBytes(StandardCharsets.UTF_8));

        verify(structureReplicaApplier).apply(
                eq(ObjectChangeType.CREATED),
                eq("root.platform.devices.d2"),
                eq(null)
        );
    }

    @Test
    void ignoresVariableUpdatedWithoutSnapshotValue() throws Exception {
        processor = newProcessor("replica-2");
        String json = """
                {
                  "type": "VARIABLE_UPDATED",
                  "path": "root.platform.devices.d1",
                  "variableName": "temperature",
                  "source": "replica-1"
                }
                """;
        assertTrue(processor.offer(json.getBytes(StandardCharsets.UTF_8)));
        verify(replicaApplier, never()).apply(any(), any(), any(), any());
        verify(structureReplicaApplier, never()).apply(any(), anyString(), any());
    }

    @Test
    void ignoresLiveSnapshotWhenNoObserverOnFollower() throws Exception {
        processor = newProcessor("replica-2");
        when(variableSubscriptionRegistry.interest(anyString(), anyString())).thenReturn(interest);
        when(interest.liveObserver()).thenReturn(false);
        String json = """
                {
                  "type": "VARIABLE_UPDATED",
                  "path": "root.platform.devices.d1",
                  "variableName": "temperature",
                  "source": "replica-1",
                  "value": {"schema":{"name":"telemetry","fields":[{"name":"value","type":"DOUBLE"}]},"values":{"value":42.5}},
                  "observedAt": "2026-07-10T12:00:00Z"
                }
                """;
        assertTrue(processor.offer(json.getBytes(StandardCharsets.UTF_8)));
        verify(replicaApplier, never()).apply(any(), any(), any(), any());
    }

    @Test
    void evictsOldestLiveLaneWhenUniqueKeyCapacityExceeded() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        NatsProperties tinyQueue = new NatsProperties(
                true,
                "nats://localhost:4222",
                true,
                "replica-2",
                false,
                "ispf-automation",
                24,
                "ispf-replica-",
                1,
                false,
                1,
                1,
                50,
                6,
                30
        );
        processor = new NatsReplicaEventProcessor(
                tinyQueue,
                objectMapper,
                replicaApplier,
                structureReplicaApplier,
                variableSubscriptionRegistry
        );
        when(variableSubscriptionRegistry.interest(anyString(), anyString())).thenReturn(interest);
        when(interest.liveObserver()).thenReturn(true);
        DataRecord value = DataRecord.single(
                DataSchema.builder("telemetry").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        );
        for (int i = 0; i < 2; i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", "VARIABLE_UPDATED");
            body.put("path", "root.platform.devices.d" + i);
            body.put("variableName", "temperature");
            body.put("source", "replica-1");
            body.put("value", value);
            body.put("observedAt", "2026-07-10T12:00:00Z");
            assertTrue(processor.offer(objectMapper.writeValueAsBytes(body)));
        }
        processor.shutdown();
    }

    @Test
    void liveVariableSnapshotsCoalesceByPathAndVariable() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        NatsProperties tinyQueue = new NatsProperties(
                true,
                "nats://localhost:4222",
                true,
                "replica-2",
                false,
                "ispf-automation",
                24,
                "ispf-replica-",
                2,
                false,
                1,
                1,
                50,
                6,
                30
        );
        processor = new NatsReplicaEventProcessor(
                tinyQueue,
                objectMapper,
                replicaApplier,
                structureReplicaApplier,
                variableSubscriptionRegistry
        );
        when(variableSubscriptionRegistry.interest(anyString(), anyString())).thenReturn(interest);
        when(interest.liveObserver()).thenReturn(true);
        DataRecord value = DataRecord.single(
                DataSchema.builder("telemetry").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "VARIABLE_UPDATED");
        body.put("path", "root.platform.devices.d1");
        body.put("variableName", "temperature");
        body.put("source", "replica-1");
        body.put("value", value);
        body.put("observedAt", "2026-07-10T12:00:00Z");
        byte[] payload = objectMapper.writeValueAsBytes(body);

        for (int i = 0; i < 100; i++) {
            assertTrue(processor.offer(payload), "coalesce should accept duplicate live-variable lane");
        }
        processor.shutdown();
    }

    @Test
    void structuralUpdatedCoalescesByPath() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        processor = newProcessor("replica-2", objectMapper);
        String json = """
                {
                  "type": "UPDATED",
                  "path": "root.platform.devices.d1",
                  "source": "replica-1"
                }
                """;
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 100; i++) {
            assertTrue(processor.offer(payload), "coalesce should accept duplicate structural UPDATED lane");
        }
        processor.shutdown();
    }

    private NatsReplicaEventProcessor newProcessor(String replicaId) {
        return newProcessor(replicaId, new ObjectMapper());
    }

    private NatsReplicaEventProcessor newProcessor(String replicaId, ObjectMapper objectMapper) {
        NatsProperties properties = new NatsProperties(
                true,
                "nats://localhost:4222",
                true,
                replicaId,
                false,
                "ispf-automation",
                24,
                "ispf-replica-",
                1024,
                false,
                1,
                1,
                50,
                6,
                30
        );
        return new NatsReplicaEventProcessor(
                properties,
                objectMapper,
                replicaApplier,
                structureReplicaApplier,
                variableSubscriptionRegistry
        );
    }
}
