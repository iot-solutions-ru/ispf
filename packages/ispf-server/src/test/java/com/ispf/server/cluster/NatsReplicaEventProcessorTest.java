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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NatsReplicaEventProcessorTest {

    private static final String BLOCKER = "root.platform.devices.blocker";
    private static final String PATH_A = "root.platform.devices.a";
    private static final String PATH_B = "root.platform.devices.b";

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
        assertTrue(processor.offer(json.getBytes(StandardCharsets.UTF_8), ReplicaDelivery.NONE));
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
        assertTrue(processor.offer(json.getBytes(StandardCharsets.UTF_8), ReplicaDelivery.NONE));
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
            assertTrue(processor.offer(objectMapper.writeValueAsBytes(body), ReplicaDelivery.NONE));
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
            assertTrue(processor.offer(payload, ReplicaDelivery.NONE), "coalesce should accept duplicate live-variable lane");
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
            assertTrue(processor.offer(payload, ReplicaDelivery.NONE), "coalesce should accept duplicate structural UPDATED lane");
        }
        processor.shutdown();
    }

    @Test
    void structuralEventIsAckedOnlyAfterItIsApplied() throws Exception {
        processor = newProcessor("replica-2");
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockApplyOn(BLOCKER, applying, release);
        RecordingDelivery delivery = new RecordingDelivery();

        assertTrue(processor.offer(structural("CREATED", BLOCKER), delivery));
        assertTrue(applying.await(5, TimeUnit.SECONDS));
        assertFalse(delivery.acked(), "must not ack while the event is still being applied");

        release.countDown();
        assertTrue(delivery.awaitAck());
        assertFalse(delivery.naked());
    }

    @Test
    void fullStructuralFifoNaksTheEventInsteadOfAckingIt() throws Exception {
        processor = newProcessor("replica-2", new ObjectMapper(), 1);
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockApplyOn(BLOCKER, applying, release);
        RecordingDelivery queued = new RecordingDelivery();
        RecordingDelivery overflow = new RecordingDelivery();

        processor.offer(structural("CREATED", BLOCKER), new RecordingDelivery());
        assertTrue(applying.await(5, TimeUnit.SECONDS));
        assertTrue(processor.offer(structural("CREATED", PATH_A), queued));
        assertFalse(processor.offer(structural("DELETED", PATH_B), overflow));

        assertTrue(overflow.naked());
        assertFalse(overflow.acked());
        release.countDown();
        assertTrue(queued.awaitAck());
        verify(structureReplicaApplier, never()).apply(any(), eq(PATH_B), any());
    }

    @Test
    void fullStructuralUpdatedLaneRejectsTheNewPathInsteadOfEvictingAnAckedOne() throws Exception {
        processor = newProcessor("replica-2", new ObjectMapper(), 1);
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockApplyOn(BLOCKER, applying, release);
        RecordingDelivery first = new RecordingDelivery();
        RecordingDelivery second = new RecordingDelivery();

        processor.offer(structural("CREATED", BLOCKER), new RecordingDelivery());
        assertTrue(applying.await(5, TimeUnit.SECONDS));
        assertTrue(processor.offer(structural("UPDATED", PATH_A), first));
        assertFalse(processor.offer(structural("UPDATED", PATH_B), second));

        assertTrue(second.naked());
        release.countDown();
        assertTrue(first.awaitAck());
        verify(structureReplicaApplier).apply(ObjectChangeType.UPDATED, PATH_A, null);
        verify(structureReplicaApplier, never()).apply(any(), eq(PATH_B), any());
    }

    @Test
    void coalescedStructuralUpdateAcksTheSupersededMessage() throws Exception {
        processor = newProcessor("replica-2");
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockApplyOn(BLOCKER, applying, release);
        RecordingDelivery older = new RecordingDelivery();
        RecordingDelivery newer = new RecordingDelivery();

        processor.offer(structural("CREATED", BLOCKER), new RecordingDelivery());
        assertTrue(applying.await(5, TimeUnit.SECONDS));
        processor.offer(structural("UPDATED", PATH_A), older);
        processor.offer(structural("UPDATED", PATH_A), newer);

        assertTrue(older.acked());
        assertFalse(newer.acked());
        release.countDown();
        assertTrue(newer.awaitAck());
        verify(structureReplicaApplier, times(1)).apply(ObjectChangeType.UPDATED, PATH_A, null);
    }

    @Test
    void failedStructuralApplyIsNakedAndTheWorkerKeepsDraining() throws Exception {
        processor = newProcessor("replica-2");
        doAnswer(invocation -> {
            if (PATH_A.equals(invocation.getArgument(1))) {
                throw new IllegalStateException("db down");
            }
            return null;
        }).when(structureReplicaApplier).apply(any(), anyString(), any());
        RecordingDelivery failing = new RecordingDelivery();
        RecordingDelivery next = new RecordingDelivery();

        processor.offer(structural("CREATED", PATH_A), failing);
        processor.offer(structural("CREATED", PATH_B), next);

        assertTrue(failing.awaitNak());
        assertFalse(failing.acked());
        assertTrue(next.awaitAck());
    }

    @Test
    void eventFiredIsAckedWithoutTakingStructuralCapacity() throws Exception {
        processor = newProcessor("replica-2", new ObjectMapper(), 1);
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockApplyOn(BLOCKER, applying, release);
        RecordingDelivery eventFired = new RecordingDelivery();
        RecordingDelivery created = new RecordingDelivery();

        processor.offer(structural("CREATED", BLOCKER), new RecordingDelivery());
        assertTrue(applying.await(5, TimeUnit.SECONDS));
        assertTrue(processor.offer(structural("EVENT_FIRED", PATH_A), eventFired));
        assertTrue(eventFired.acked());
        assertTrue(processor.offer(structural("CREATED", PATH_B), created));

        release.countDown();
        assertTrue(created.awaitAck());
        verify(structureReplicaApplier, never()).apply(eq(ObjectChangeType.EVENT_FIRED), anyString(), any());
    }

    @Test
    void structuralEventsDrainBeforeLiveSnapshots() throws Exception {
        processor = newProcessor("replica-2");
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockApplyOn(BLOCKER, applying, release);
        when(variableSubscriptionRegistry.interest(anyString(), anyString())).thenReturn(interest);
        when(interest.liveObserver()).thenReturn(true);
        CountDownLatch liveApplied = new CountDownLatch(1);
        doAnswer(invocation -> {
            liveApplied.countDown();
            return null;
        }).when(replicaApplier).apply(anyString(), anyString(), any(), any());
        RecordingDelivery created = new RecordingDelivery();

        processor.offer(structural("CREATED", BLOCKER), new RecordingDelivery());
        assertTrue(applying.await(5, TimeUnit.SECONDS));
        processor.offer(liveSnapshot(PATH_A), ReplicaDelivery.NONE);
        processor.offer(structural("CREATED", PATH_B), created);
        release.countDown();

        assertTrue(liveApplied.await(5, TimeUnit.SECONDS));
        assertTrue(created.awaitAck());
        InOrder order = inOrder(structureReplicaApplier, replicaApplier);
        order.verify(structureReplicaApplier).apply(ObjectChangeType.CREATED, PATH_B, null);
        order.verify(replicaApplier).apply(eq(PATH_A), eq("temperature"), any(), any());
    }

    private void blockApplyOn(String path, CountDownLatch applying, CountDownLatch release) {
        doAnswer(invocation -> {
            if (path.equals(invocation.getArgument(1))) {
                applying.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return null;
        }).when(structureReplicaApplier).apply(any(), anyString(), any());
    }

    private static byte[] structural(String type, String path) {
        return """
                {"type":"%s","path":"%s","source":"replica-1"}
                """.formatted(type, path).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] liveSnapshot(String path) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "VARIABLE_UPDATED");
        body.put("path", path);
        body.put("variableName", "temperature");
        body.put("source", "replica-1");
        body.put("value", DataRecord.single(
                DataSchema.builder("telemetry").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        ));
        body.put("observedAt", "2026-07-10T12:00:00Z");
        return new ObjectMapper().writeValueAsBytes(body);
    }

    private NatsReplicaEventProcessor newProcessor(String replicaId) {
        return newProcessor(replicaId, new ObjectMapper());
    }

    private NatsReplicaEventProcessor newProcessor(String replicaId, ObjectMapper objectMapper) {
        return newProcessor(replicaId, objectMapper, 1024);
    }

    private NatsReplicaEventProcessor newProcessor(String replicaId, ObjectMapper objectMapper, int laneCapacity) {
        NatsProperties properties = new NatsProperties(
                true,
                "nats://localhost:4222",
                true,
                replicaId,
                false,
                "ispf-automation",
                24,
                "ispf-replica-",
                laneCapacity,
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

    private static final class RecordingDelivery implements ReplicaDelivery {

        private final CountDownLatch ackLatch = new CountDownLatch(1);
        private final CountDownLatch nakLatch = new CountDownLatch(1);

        @Override
        public void ack() {
            ackLatch.countDown();
        }

        @Override
        public void nak() {
            nakLatch.countDown();
        }

        @Override
        public boolean redeliverable() {
            return true;
        }

        boolean acked() {
            return ackLatch.getCount() == 0;
        }

        boolean naked() {
            return nakLatch.getCount() == 0;
        }

        boolean awaitAck() throws InterruptedException {
            return ackLatch.await(5, TimeUnit.SECONDS);
        }

        boolean awaitNak() throws InterruptedException {
            return nakLatch.await(5, TimeUnit.SECONDS);
        }
    }
}
