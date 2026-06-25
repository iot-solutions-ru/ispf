package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RuntimeTelemetryCoalescer {

    private final RuntimeTelemetryProperties properties;
    private final DeviceTelemetryPolicyService policyService;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, PendingUpdate> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DataRecord> lastPublished = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> deviceFlushScheduled = new ConcurrentHashMap<>();

    public RuntimeTelemetryCoalescer(
            RuntimeTelemetryProperties properties,
            DeviceTelemetryPolicyService policyService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.properties = properties;
        this.policyService = policyService;
        this.eventPublisher = eventPublisher;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "runtime-telemetry-coalescer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void recordUpdate(String path, String variableName, DataRecord value) {
        String key = key(path, variableName);
        if (valuesEqual(lastPublished.get(key), value)) {
            return;
        }
        if (!properties.isEnabled()) {
            publishIfChanged(path, variableName, value, key);
            return;
        }
        pending.put(key, new PendingUpdate(path, variableName, value));
        scheduleFlushForDevice(path);
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

    private void scheduleFlushForDevice(String devicePath) {
        long coalesceMs = policyService.coalesceMs(devicePath);
        AtomicBoolean scheduled = deviceFlushScheduled.computeIfAbsent(devicePath, ignored -> new AtomicBoolean(false));
        if (scheduled.compareAndSet(false, true)) {
            scheduler.schedule(() -> {
                scheduled.set(false);
                flushDevice(devicePath);
            }, coalesceMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushAll() {
        Map<String, PendingUpdate> snapshot = new HashMap<>(pending);
        pending.clear();
        for (PendingUpdate update : snapshot.values()) {
            publishIfChanged(update.path(), update.variableName(), update.value(), key(update.path(), update.variableName()));
        }
    }

    private void flushDevice(String devicePath) {
        Map<String, PendingUpdate> snapshot = new HashMap<>();
        pending.forEach((key, update) -> {
            if (devicePath.equals(update.path())) {
                snapshot.put(key, update);
            }
        });
        snapshot.keySet().forEach(pending::remove);
        for (PendingUpdate update : snapshot.values()) {
            publishIfChanged(update.path(), update.variableName(), update.value(), key(update.path(), update.variableName()));
        }
        if (pending.keySet().stream().anyMatch(key -> devicePath.equals(pending.get(key).path()))) {
            scheduleFlushForDevice(devicePath);
        }
    }

    private void publishIfChanged(String path, String variableName, DataRecord value, String key) {
        DataRecord last = lastPublished.get(key);
        if (valuesEqual(last, value)) {
            return;
        }
        lastPublished.put(key, value);
        boolean automationEligible = policyService.automationEligible(path);
        eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(path, variableName, true, automationEligible));
    }

    private static String key(String path, String variableName) {
        return path + "|" + variableName;
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

    private record PendingUpdate(String path, String variableName, DataRecord value) {}
}
