package com.ispf.server.cluster;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.server.concurrent.ElasticWorkerLauncher;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.object.ClusterStructureReplicaApplier;
import com.ispf.server.object.ClusterVariableReplicaApplier;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.pubsub.VariableChangeSubscriptionRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Offloads NATS replica-event handling from the single {@code pool-3-thread-1} dispatcher thread.
 * <p>
 * Live variable snapshots and structural {@code UPDATED} use last-value-wins coalesce per path key.
 * Structural events are acked only after they are applied and are never evicted: when a lane is full
 * the message is nak'd so JetStream redelivers it. Core NATS cannot redeliver, so such a drop is logged.
 */
@Component
@ConditionalOnProperty(prefix = "ispf.nats", name = "enabled", havingValue = "true")
public class NatsReplicaEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(NatsReplicaEventProcessor.class);
    private static final int DRAIN_BATCH = 64;
    private static final long WARN_INTERVAL_MS = 30_000L;

    private enum Ingress {
        IGNORED,
        LIVE_QUEUED,
        STRUCTURAL_QUEUED,
        REJECTED
    }

    private final NatsProperties properties;
    private final ObjectMapper objectMapper;
    private final ClusterVariableReplicaApplier replicaApplier;
    private final ClusterStructureReplicaApplier structureReplicaApplier;
    private final VariableChangeSubscriptionRegistry variableSubscriptionRegistry;
    private final int laneCapacity;
    private final ConcurrentHashMap<String, PendingLiveSnapshot> livePendingByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingStructural> structuralUpdatedByPath = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<PendingStructural> structuralFifo;
    private final Object ingressGate = new Object();
    private final ElasticWorkerLauncher launcher;
    private final AtomicLong nakedForRedelivery = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong evicted = new AtomicLong();
    private final AtomicLong applyFailures = new AtomicLong();
    private final AtomicLong lastDropLogAt = new AtomicLong(0);
    private final AtomicLong lastApplyFailureLogAt = new AtomicLong(0);

    public NatsReplicaEventProcessor(
            NatsProperties properties,
            ObjectMapper objectMapper,
            ClusterVariableReplicaApplier replicaApplier,
            ClusterStructureReplicaApplier structureReplicaApplier,
            VariableChangeSubscriptionRegistry variableSubscriptionRegistry
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.replicaApplier = replicaApplier;
        this.structureReplicaApplier = structureReplicaApplier;
        this.variableSubscriptionRegistry = variableSubscriptionRegistry;
        this.laneCapacity = Math.max(1, properties.replicaConsumerQueueCapacity());
        this.structuralFifo = new LinkedBlockingQueue<>(laneCapacity);
        this.launcher = new ElasticWorkerLauncher(
                properties.resolvedReplicaConsumerElastic(),
                () -> livePendingByKey.size() + structuralUpdatedByPath.size() + structuralFifo.size(),
                "nats-replica-consumer",
                this::drainBatch
        );
        launcher.start();
        var elastic = properties.resolvedReplicaConsumerElastic();
        log.info(
                "NATS replica consumer started (threads={}-{}, elastic={}, laneCapacity={}, "
                        + "structuralUpdatedCoalesce=true, drainBatch={})",
                elastic.resolvedMinWorkers(),
                elastic.resolvedMaxWorkers(),
                elastic.enabled(),
                laneCapacity,
                DRAIN_BATCH
        );
    }

    /**
     * Queues the event and settles {@code delivery}: ack when ignored or once applied, nak when rejected.
     *
     * @return false when the event was rejected because its lane is full
     */
    public boolean offer(byte[] payload, ReplicaDelivery delivery) {
        Ingress ingress = enqueue(payload, delivery);
        switch (ingress) {
            case IGNORED, LIVE_QUEUED -> delivery.ack();
            case REJECTED -> {
                delivery.nak();
                recordRejected(delivery.redeliverable());
            }
            case STRUCTURAL_QUEUED -> {
            }
        }
        return ingress != Ingress.REJECTED;
    }

    private Ingress enqueue(byte[] payload, ReplicaDelivery delivery) {
        if (payload == null || payload.length == 0) {
            return Ingress.IGNORED;
        }
        try {
            Map<String, Object> body = objectMapper.readValue(payload, new TypeReference<>() {
            });
            Object source = body.get("source");
            if (source != null && properties.replicaId().equals(String.valueOf(source))) {
                return Ingress.IGNORED;
            }
            ObjectChangeType type = ObjectChangeType.valueOf(String.valueOf(body.get("type")));
            String path = String.valueOf(body.get("path"));
            String variableName = body.get("variableName") != null
                    ? String.valueOf(body.get("variableName"))
                    : null;
            if (type == ObjectChangeType.VARIABLE_UPDATED && variableName != null) {
                Object rawValue = body.get("value");
                if (rawValue == null) {
                    return Ingress.IGNORED;
                }
                DataRecord value = objectMapper.convertValue(body.get("value"), DataRecord.class);
                Instant observedAt = parseInstant(body.get("observedAt"));
                if (!variableSubscriptionRegistry.interest(path, variableName).liveObserver()) {
                    return Ingress.IGNORED;
                }
                return offerLiveSnapshot(path, variableName, value, observedAt);
            }
            if (!ClusterStructureReplicaApplier.appliesTo(type)) {
                return Ingress.IGNORED;
            }
            return offerStructural(new PendingStructural(type, path, variableName, delivery));
        } catch (Exception ex) {
            log.warn("Failed to classify NATS replica event: {}", ex.getMessage());
            return Ingress.IGNORED;
        }
    }

    @PreDestroy
    void shutdown() {
        launcher.close();
    }

    void processPayload(byte[] payload) {
        try {
            Map<String, Object> body = objectMapper.readValue(payload, new TypeReference<>() {
            });
            Object source = body.get("source");
            if (source != null && properties.replicaId().equals(String.valueOf(source))) {
                return;
            }
            ObjectChangeType type = ObjectChangeType.valueOf(String.valueOf(body.get("type")));
            String path = String.valueOf(body.get("path"));
            String variableName = body.get("variableName") != null
                    ? String.valueOf(body.get("variableName"))
                    : null;
            if (type == ObjectChangeType.VARIABLE_UPDATED && variableName != null) {
                if (!body.containsKey("value") || body.get("value") == null) {
                    return;
                }
                DataRecord value = objectMapper.convertValue(body.get("value"), DataRecord.class);
                Instant observedAt = parseInstant(body.get("observedAt"));
                replicaApplier.apply(path, variableName, value, observedAt);
                return;
            }
            structureReplicaApplier.apply(type, path, variableName);
        } catch (Exception ex) {
            log.warn("Failed to handle NATS replica event: {}", ex.getMessage());
        }
    }

    private Ingress offerLiveSnapshot(
            String path,
            String variableName,
            DataRecord value,
            Instant observedAt
    ) {
        synchronized (ingressGate) {
            String key = liveVariableKey(path, variableName);
            PendingLiveSnapshot snapshot = new PendingLiveSnapshot(path, variableName, value, observedAt);
            PendingLiveSnapshot previous = livePendingByKey.put(key, snapshot);
            if (previous != null) {
                coalesced.incrementAndGet();
                launcher.signalWork();
                return Ingress.LIVE_QUEUED;
            }
            while (livePendingByKey.size() > laneCapacity) {
                if (!evictOtherLiveLane(key)) {
                    livePendingByKey.remove(key, snapshot);
                    return Ingress.REJECTED;
                }
                evicted.incrementAndGet();
            }
            launcher.signalWork();
            return Ingress.LIVE_QUEUED;
        }
    }

    private Ingress offerStructural(PendingStructural pending) {
        PendingStructural superseded = null;
        synchronized (ingressGate) {
            if (pending.type() == ObjectChangeType.UPDATED) {
                superseded = structuralUpdatedByPath.put(pending.path(), pending);
                if (superseded == null && structuralUpdatedByPath.size() > laneCapacity) {
                    structuralUpdatedByPath.remove(pending.path(), pending);
                    return Ingress.REJECTED;
                }
            } else if (!structuralFifo.offer(pending)) {
                return Ingress.REJECTED;
            }
        }
        if (superseded != null) {
            coalesced.incrementAndGet();
            superseded.delivery().ack();
        }
        launcher.signalWork();
        return Ingress.STRUCTURAL_QUEUED;
    }

    private void recordRejected(boolean redelivered) {
        (redelivered ? nakedForRedelivery : dropped).incrementAndGet();
        if (!shouldWarn(lastDropLogAt)) {
            return;
        }
        long totalDropped = dropped.get();
        log.warn(
                "NATS replica consumer overloaded; nakedForRedelivery={} droppedWithoutRedelivery={} "
                        + "coalescedMessages={} evictedLiveLanes={} livePending={} structuralUpdatedPending={} "
                        + "structuralFifoPending={}{}",
                nakedForRedelivery.get(),
                totalDropped,
                coalesced.get(),
                evicted.get(),
                livePendingByKey.size(),
                structuralUpdatedByPath.size(),
                structuralFifo.size(),
                totalDropped > 0 ? " (core NATS cannot redeliver; enable JetStream for loss-free replica sync)" : ""
        );
    }

    private void recordApplyFailure(String path, RuntimeException ex, boolean redelivered) {
        long total = applyFailures.incrementAndGet();
        if (!shouldWarn(lastApplyFailureLogAt)) {
            return;
        }
        log.warn(
                "Failed to apply NATS replica event (applyFailures={}, latestPath={}, redelivered={}): {}",
                total,
                path,
                redelivered,
                ex.getMessage()
        );
    }

    private static boolean shouldWarn(AtomicLong lastLogAt) {
        long now = System.currentTimeMillis();
        long last = lastLogAt.get();
        return now - last >= WARN_INTERVAL_MS && lastLogAt.compareAndSet(last, now);
    }

    private boolean drainBatch() {
        int processed = 0;
        for (int i = 0; i < DRAIN_BATCH; i++) {
            PendingStructural structural = pollStructuralFifo();
            if (structural == null) {
                structural = pollStructuralUpdated();
            }
            if (structural != null) {
                applyStructural(structural);
                processed++;
                continue;
            }
            PendingLiveSnapshot live = pollLivePending();
            if (live == null) {
                break;
            }
            applyLive(live);
            processed++;
        }
        return processed > 0;
    }

    private void applyStructural(PendingStructural pending) {
        try {
            structureReplicaApplier.apply(pending.type(), pending.path(), pending.variableName());
        } catch (RuntimeException ex) {
            pending.delivery().nak();
            recordApplyFailure(pending.path(), ex, pending.delivery().redeliverable());
            return;
        }
        pending.delivery().ack();
    }

    private void applyLive(PendingLiveSnapshot live) {
        try {
            replicaApplier.apply(live.path(), live.variableName(), live.value(), live.observedAt());
        } catch (RuntimeException ex) {
            recordApplyFailure(live.path(), ex, false);
        }
    }

    private PendingStructural pollStructuralFifo() {
        synchronized (ingressGate) {
            return structuralFifo.poll();
        }
    }

    private PendingStructural pollStructuralUpdated() {
        for (Map.Entry<String, PendingStructural> entry : structuralUpdatedByPath.entrySet()) {
            if (structuralUpdatedByPath.remove(entry.getKey(), entry.getValue())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private PendingLiveSnapshot pollLivePending() {
        for (Map.Entry<String, PendingLiveSnapshot> entry : livePendingByKey.entrySet()) {
            if (livePendingByKey.remove(entry.getKey(), entry.getValue())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean evictOtherLiveLane(String protectedKey) {
        for (Map.Entry<String, PendingLiveSnapshot> entry : livePendingByKey.entrySet()) {
            if (entry.getKey().equals(protectedKey)) {
                continue;
            }
            if (livePendingByKey.remove(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static String liveVariableKey(String path, String variableName) {
        return path + "\0" + variableName;
    }

    private static Instant parseInstant(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(raw));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record PendingLiveSnapshot(String path, String variableName, DataRecord value, Instant observedAt) {
    }

    private record PendingStructural(
            ObjectChangeType type,
            String path,
            String variableName,
            ReplicaDelivery delivery
    ) {
    }
}
