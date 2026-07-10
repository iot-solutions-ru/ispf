package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.workflow.NatsEventBridge;
import jakarta.annotation.PreDestroy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADR-0029: coalesced NATS fan-out of live variable value snapshots from owner to follower replicas.
 */
@Component
public class ClusterLiveVariableReplicaPublisher {

    private final ClusterProperties clusterProperties;
    private final NatsProperties natsProperties;
    private final ObjectManager objectManager;
    private final NatsEventBridge natsEventBridge;
    private final ConcurrentHashMap<String, PendingLiveVariable> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cluster-live-variable-nats");
        thread.setDaemon(true);
        return thread;
    });

    public ClusterLiveVariableReplicaPublisher(
            ClusterProperties clusterProperties,
            NatsProperties natsProperties,
            ObjectManager objectManager,
            NatsEventBridge natsEventBridge
    ) {
        this.clusterProperties = clusterProperties;
        this.natsProperties = natsProperties;
        this.objectManager = objectManager;
        this.natsEventBridge = natsEventBridge;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (!clusterProperties.isLiveVariableSyncActive()
                || !natsProperties.enabled()
                || !natsProperties.replicaEventsEnabled()
                || event.replicaIngress()) {
            return;
        }
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        DataRecord value = objectManager.tree().findByPath(event.path())
                .flatMap(node -> node.getVariable(event.variableName()))
                .flatMap(com.ispf.core.object.Variable::value)
                .orElse(null);
        if (value == null) {
            return;
        }
        if (!clusterProperties.isLiveVariableSyncCoalesceActive()) {
            natsEventBridge.publishLiveVariableReplicaSync(
                    event.path(),
                    event.variableName(),
                    value,
                    event.observedAt()
            );
            return;
        }
        String key = event.path() + "\0" + event.variableName();
        pending.put(key, new PendingLiveVariable(
                event.path(),
                event.variableName(),
                value,
                event.observedAt()
        ));
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) {
            return;
        }
        long delayMs = clusterProperties.liveVariableSyncCoalesceMs();
        scheduler.schedule(this::flushPending, delayMs, TimeUnit.MILLISECONDS);
    }

    private void flushPending() {
        flushScheduled.set(false);
        if (pending.isEmpty()) {
            return;
        }
        Map<String, PendingLiveVariable> batch = Map.copyOf(pending);
        pending.clear();
        for (PendingLiveVariable update : batch.values()) {
            natsEventBridge.publishLiveVariableReplicaSync(
                    update.path(),
                    update.variableName(),
                    update.value(),
                    update.observedAt()
            );
        }
        if (!pending.isEmpty()) {
            scheduleFlush();
        }
    }

    private record PendingLiveVariable(
            String path,
            String variableName,
            DataRecord value,
            Instant observedAt
    ) {
    }
}
