package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RuntimeTelemetryCoalescer {

    private final RuntimeTelemetryProperties properties;
    private final DeviceTelemetryPolicyService policyService;
    private final ApplicationEventPublisher eventPublisher;
    private final MqttGatewayIngressDispatchService gatewayIngressDispatch;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, PendingUpdate> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DataRecord> lastPublished = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> laneFlushScheduled = new ConcurrentHashMap<>();

    public RuntimeTelemetryCoalescer(
            RuntimeTelemetryProperties properties,
            DeviceTelemetryPolicyService policyService,
            ApplicationEventPublisher eventPublisher,
            @Lazy MqttGatewayIngressDispatchService gatewayIngressDispatch
    ) {
        this.properties = properties;
        this.policyService = policyService;
        this.eventPublisher = eventPublisher;
        this.gatewayIngressDispatch = gatewayIngressDispatch;
        int schedulerThreads = Math.max(1, properties.getCoalesceSchedulerThreads());
        AtomicInteger threadIndex = new AtomicInteger();
        this.scheduler = Executors.newScheduledThreadPool(schedulerThreads, r -> {
            Thread thread = new Thread(r, "runtime-telemetry-coalescer-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void recordUpdate(String path, String variableName, DataRecord value) {
        recordUpdate(path, variableName, value, null);
    }

    public void recordUpdate(String path, String variableName, DataRecord value, Instant observedAt) {
        String coalesceKey = resolveCoalesceKey(path, variableName, value);
        if (valuesEqual(lastPublished.get(coalesceKey), value)) {
            return;
        }
        if (!properties.isEnabled()) {
            publishIfChanged(path, variableName, value, coalesceKey, observedAt);
            return;
        }
        pending.put(coalesceKey, new PendingUpdate(path, variableName, value, observedAt));
        scheduleFlushForLane(coalesceKey, path);
    }

    public void flushNow() {
        flushAll();
    }

    @PreDestroy
    public void shutdown() {
        flushAll();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private void scheduleFlushForLane(String coalesceKey, String devicePath) {
        long coalesceMs = policyService.coalesceMs(devicePath);
        AtomicBoolean scheduled = laneFlushScheduled.computeIfAbsent(coalesceKey, ignored -> new AtomicBoolean(false));
        if (scheduled.compareAndSet(false, true)) {
            scheduler.schedule(() -> {
                scheduled.set(false);
                flushLane(coalesceKey, devicePath);
            }, coalesceMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushAll() {
        Map<String, PendingUpdate> snapshot = new HashMap<>(pending);
        pending.clear();
        for (Map.Entry<String, PendingUpdate> entry : snapshot.entrySet()) {
            PendingUpdate update = entry.getValue();
            publishIfChanged(
                    update.path(),
                    update.variableName(),
                    update.value(),
                    entry.getKey(),
                    update.observedAt()
            );
        }
    }

    private void flushLane(String coalesceKey, String devicePath) {
        PendingUpdate update = pending.remove(coalesceKey);
        if (update != null) {
            publishIfChanged(update.path(), update.variableName(), update.value(), coalesceKey, update.observedAt());
        }
        if (pending.containsKey(coalesceKey)) {
            scheduleFlushForLane(coalesceKey, devicePath);
        }
    }

    private void publishIfChanged(
            String path,
            String variableName,
            DataRecord value,
            String coalesceKey,
            Instant observedAt
    ) {
        DataRecord last = lastPublished.get(coalesceKey);
        if (valuesEqual(last, value)) {
            return;
        }
        lastPublished.put(coalesceKey, value);
        if (gatewayIngressDispatch.tryScheduleDispatch(path, variableName, value)) {
            return;
        }
        boolean automationEligible = policyService.automationEligible(path);
        eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(
                path, variableName, true, automationEligible, observedAt
        ));
    }

    private String resolveCoalesceKey(String path, String variableName, DataRecord value) {
        if (MqttGatewayIngressDispatchService.isIngressPayload(value)) {
            Optional<String> topic = MqttGatewayIngressDispatchService.ingressTopic(value);
            if (topic.isPresent() && !topic.get().isBlank()) {
                String base = path + "|" + variableName + "|" + topic.get();
                if (policyService.ingressPayloadLanes(path)) {
                    return MqttGatewayIngressDispatchService.ingressRaw(value)
                            .filter(raw -> !raw.isBlank())
                            .map(raw -> base + "|" + payloadLaneSuffix(raw))
                            .orElse(base);
                }
                return base;
            }
        }
        return path + "|" + variableName;
    }

    static String coalesceKey(String path, String variableName, DataRecord value) {
        return coalesceKey(path, variableName, value, false);
    }

    static String coalesceKey(String path, String variableName, DataRecord value, boolean ingressPayloadLanes) {
        if (MqttGatewayIngressDispatchService.isIngressPayload(value)) {
            Optional<String> topic = MqttGatewayIngressDispatchService.ingressTopic(value);
            if (topic.isPresent() && !topic.get().isBlank()) {
                String base = path + "|" + variableName + "|" + topic.get();
                if (ingressPayloadLanes) {
                    return MqttGatewayIngressDispatchService.ingressRaw(value)
                            .filter(raw -> !raw.isBlank())
                            .map(raw -> base + "|" + payloadLaneSuffix(raw))
                            .orElse(base);
                }
                return base;
            }
        }
        return path + "|" + variableName;
    }

    private static String payloadLaneSuffix(String raw) {
        return Integer.toUnsignedString(raw.hashCode(), 36);
    }

    static boolean valuesEqual(DataRecord left, DataRecord right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.rowCount() != right.rowCount()) {
            return false;
        }
        for (int i = 0; i < left.rowCount(); i++) {
            if (!Objects.equals(left.rows().get(i), right.rows().get(i))) {
                return false;
            }
        }
        return true;
    }

    private record PendingUpdate(String path, String variableName, DataRecord value, Instant observedAt) {}
}
