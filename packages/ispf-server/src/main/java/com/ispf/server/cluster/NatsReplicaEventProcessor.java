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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Offloads NATS replica-event handling from the single {@code pool-3-thread-1} dispatcher thread.
 * <p>
 * Live variable snapshots and structural {@code UPDATED} use last-value-wins coalesce per path key.
 */
@Component
@ConditionalOnProperty(prefix = "ispf.nats", name = "enabled", havingValue = "true")
public class NatsReplicaEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(NatsReplicaEventProcessor.class);
    private static final int DRAIN_BATCH = 64;
    private static final int STRUCTURAL_FIFO_CAPACITY = 4096;

    private final NatsProperties properties;
    private final ObjectMapper objectMapper;
    private final ClusterVariableReplicaApplier replicaApplier;
    private final ClusterStructureReplicaApplier structureReplicaApplier;
    private final VariableChangeSubscriptionRegistry variableSubscriptionRegistry;
    private final int liveLaneCapacity;
    private final ConcurrentHashMap<String, PendingLiveSnapshot> livePendingByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingStructural> structuralUpdatedByPath = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<PendingStructural> structuralFifo;
    private final Object ingressGate = new Object();
    private final ElasticWorkerLauncher launcher;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong evicted = new AtomicLong();
    private final AtomicLong lastDropLogAt = new AtomicLong(0);

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
        this.liveLaneCapacity = Math.max(1, properties.replicaConsumerQueueCapacity());
        this.structuralFifo = new LinkedBlockingQueue<>(STRUCTURAL_FIFO_CAPACITY);
        this.launcher = new ElasticWorkerLauncher(
                properties.resolvedReplicaConsumerElastic(),
                () -> livePendingByKey.size() + structuralUpdatedByPath.size() + structuralFifo.size(),
                "nats-replica-consumer",
                this::drainBatch
        );
        launcher.start();
        var elastic = properties.resolvedReplicaConsumerElastic();
        log.info(
                "NATS replica consumer started (threads={}-{}, elastic={}, liveLaneCapacity={}, "
                        + "structuralFifo={}, structuralUpdatedCoalesce=true, drainBatch={})",
                elastic.resolvedMinWorkers(),
                elastic.resolvedMaxWorkers(),
                elastic.enabled(),
                liveLaneCapacity,
                STRUCTURAL_FIFO_CAPACITY,
                DRAIN_BATCH
        );
    }

    /**
     * @return false when a structural event was dropped or a new live-variable lane could not be queued
     */
    public boolean offer(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return true;
        }
        try {
            Map<String, Object> body = objectMapper.readValue(payload, new TypeReference<>() {
            });
            Object source = body.get("source");
            if (source != null && properties.replicaId().equals(String.valueOf(source))) {
                return true;
            }
            ObjectChangeType type = ObjectChangeType.valueOf(String.valueOf(body.get("type")));
            String path = String.valueOf(body.get("path"));
            String variableName = body.get("variableName") != null
                    ? String.valueOf(body.get("variableName"))
                    : null;
            if (type == ObjectChangeType.VARIABLE_UPDATED && variableName != null) {
                Object rawValue = body.get("value");
                if (rawValue == null) {
                    return true;
                }
                DataRecord value = objectMapper.convertValue(body.get("value"), DataRecord.class);
                Instant observedAt = parseInstant(body.get("observedAt"));
                if (!variableSubscriptionRegistry.interest(path, variableName).liveObserver()) {
                    return true;
                }
                return offerLiveSnapshot(path, variableName, value, observedAt);
            }
            return offerStructural(type, path, variableName);
        } catch (Exception ex) {
            log.warn("Failed to classify NATS replica event: {}", ex.getMessage());
            return true;
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

    private boolean offerLiveSnapshot(
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
                return true;
            }
            while (livePendingByKey.size() > liveLaneCapacity) {
                if (!evictOtherLiveLane(key)) {
                    livePendingByKey.remove(key, snapshot);
                    recordDrop();
                    return false;
                }
                evicted.incrementAndGet();
            }
            launcher.signalWork();
            return true;
        }
    }

    private boolean offerStructural(ObjectChangeType type, String path, String variableName) {
        PendingStructural pending = new PendingStructural(type, path, variableName);
        synchronized (ingressGate) {
            if (type == ObjectChangeType.UPDATED) {
                PendingStructural previous = structuralUpdatedByPath.put(path, pending);
                if (previous != null) {
                    coalesced.incrementAndGet();
                    launcher.signalWork();
                    return true;
                }
                while (structuralUpdatedByPath.size() > liveLaneCapacity) {
                    if (!evictOtherStructuralUpdatedLane(path)) {
                        structuralUpdatedByPath.remove(path, pending);
                        recordDrop();
                        return false;
                    }
                    evicted.incrementAndGet();
                }
                launcher.signalWork();
                return true;
            }
            if (structuralFifo.offer(pending)) {
                launcher.signalWork();
                return true;
            }
            recordDrop();
            return false;
        }
    }

    private void recordDrop() {
        long totalDropped = dropped.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastDropLogAt.get();
        if (now - last >= 30_000L && lastDropLogAt.compareAndSet(last, now)) {
            log.warn(
                    "NATS replica consumer overloaded; droppedMessages={} coalescedMessages={} "
                            + "evictedLanes={} livePending={} structuralUpdatedPending={} structuralFifoPending={}",
                    totalDropped,
                    coalesced.get(),
                    evicted.get(),
                    livePendingByKey.size(),
                    structuralUpdatedByPath.size(),
                    structuralFifo.size()
            );
        }
    }

    private boolean drainBatch() {
        int processed = 0;
        for (int i = 0; i < DRAIN_BATCH; i++) {
            PendingLiveSnapshot live = pollLivePending();
            if (live != null) {
                replicaApplier.apply(live.path(), live.variableName(), live.value(), live.observedAt());
                processed++;
                continue;
            }
            PendingStructural structuralUpdated = pollStructuralUpdated();
            if (structuralUpdated != null) {
                structureReplicaApplier.apply(
                        structuralUpdated.type(),
                        structuralUpdated.path(),
                        structuralUpdated.variableName()
                );
                processed++;
                continue;
            }
            PendingStructural structural = pollStructuralFifo();
            if (structural == null) {
                break;
            }
            structureReplicaApplier.apply(
                    structural.type(),
                    structural.path(),
                    structural.variableName()
            );
            processed++;
        }
        return processed > 0;
    }

    private PendingStructural pollStructuralFifo() {
        synchronized (ingressGate) {
            return structuralFifo.poll();
        }
    }

    private PendingStructural pollStructuralUpdated() {
        Iterator<Map.Entry<String, PendingStructural>> iterator = structuralUpdatedByPath.entrySet().iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        Map.Entry<String, PendingStructural> entry = iterator.next();
        iterator.remove();
        return entry.getValue();
    }

    private PendingLiveSnapshot pollLivePending() {
        Iterator<Map.Entry<String, PendingLiveSnapshot>> iterator = livePendingByKey.entrySet().iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        Map.Entry<String, PendingLiveSnapshot> entry = iterator.next();
        iterator.remove();
        return entry.getValue();
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

    private boolean evictOtherStructuralUpdatedLane(String protectedPath) {
        for (Map.Entry<String, PendingStructural> entry : structuralUpdatedByPath.entrySet()) {
            if (entry.getKey().equals(protectedPath)) {
                continue;
            }
            if (structuralUpdatedByPath.remove(entry.getKey(), entry.getValue())) {
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

    private record PendingStructural(ObjectChangeType type, String path, String variableName) {
    }
}
