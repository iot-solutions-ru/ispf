package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.object.ClusterVariableReplicaApplier;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NatsReplicaEventProcessorTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ClusterVariableReplicaApplier replicaApplier;

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
        verify(eventPublisher, never()).publishEvent(any());
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

        verify(eventPublisher, never()).publishEvent(any());
        verify(replicaApplier, never()).apply(any(), any(), any(), any());
    }

    @Test
    void publishesStructuralReplicaEventLocally() throws Exception {
        processor = newProcessor("replica-2");
        String json = """
                {
                  "type": "CREATED",
                  "path": "root.platform.devices.d2",
                  "source": "replica-1"
                }
                """;
        processor.processPayload(json.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<ObjectChangeEvent> captor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(ObjectChangeType.CREATED, captor.getValue().type());
        assertEquals("root.platform.devices.d2", captor.getValue().path());
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
        verify(eventPublisher, never()).publishEvent(any());
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
                eventPublisher,
                replicaApplier
        );
        processor.shutdown();
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
                eventPublisher,
                replicaApplier
        );
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
                eventPublisher,
                replicaApplier
        );
    }
}
