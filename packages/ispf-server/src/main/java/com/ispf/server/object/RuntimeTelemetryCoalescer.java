package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataRecordValues;
import com.ispf.core.object.HistorySampleMode;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import com.ispf.server.history.TelemetryHistorianFastPath;
import com.ispf.driver.ingress.ElasticWorkerScaler;
import com.ispf.driver.ingress.ThreadPoolResize;
import jakarta.annotation.PreDestroy;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RuntimeTelemetryCoalescer {

    private final RuntimeTelemetryProperties properties;
    private final DeviceTelemetryPolicyService policyService;
    private final ObjectChangePublicationService publicationService;
    private final MqttGatewayIngressDispatchService gatewayIngressDispatch;
    private final TelemetryHistorianFastPath historianFastPath;
    private final TelemetryIngressDispatcher telemetryIngressDispatcher;
    private ScheduledThreadPoolExecutor scheduler;
    private ElasticWorkerScaler schedulerScaler;
    private final ConcurrentHashMap<String, PendingUpdate> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DataRecord> lastPublished = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> laneFlushScheduled = new ConcurrentHashMap<>();
    private volatile boolean schedulerStarted;

    public RuntimeTelemetryCoalescer(
            RuntimeTelemetryProperties properties,
            DeviceTelemetryPolicyService policyService,
            ObjectChangePublicationService publicationService,
            @Lazy MqttGatewayIngressDispatchService gatewayIngressDispatch,
            @Lazy TelemetryHistorianFastPath historianFastPath,
            @Lazy TelemetryIngressDispatcher telemetryIngressDispatcher
    ) {
        this.properties = properties;
        this.policyService = policyService;
        this.publicationService = publicationService;
        this.gatewayIngressDispatch = gatewayIngressDispatch;
        this.historianFastPath = historianFastPath;
        this.telemetryIngressDispatcher = telemetryIngressDispatcher;
    }

    public synchronized void ensureSchedulerStarted() {
        if (schedulerStarted) {
            return;
        }
        int minWorkers = properties.resolvedCoalesceSchedulerThreadsMin();
        int maxWorkers = properties.resolvedCoalesceSchedulerThreadsMax();
        AtomicInteger threadIndex = new AtomicInteger();
        scheduler = new ScheduledThreadPoolExecutor(minWorkers, runnable -> {
            Thread thread = new Thread(runnable, "runtime-telemetry-coalescer-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setMaximumPoolSize(maxWorkers);
        scheduler.setRemoveOnCancelPolicy(true);
        if (properties.isCoalesceSchedulerElastic()) {
            schedulerScaler = new ElasticWorkerScaler(
                    minWorkers,
                    maxWorkers,
                    properties.getCoalesceSchedulerScaleUpThreshold(),
                    properties.getCoalesceSchedulerScaleDownSteps()
            );
        }
        schedulerStarted = true;
    }

    public boolean isSchedulerStarted() {
        return schedulerStarted;
    }

    private void adjustSchedulerWorkers() {
        if (schedulerScaler == null || scheduler == null) {
            return;
        }
        schedulerScaler.adjust(pending.size());
        int minWorkers = properties.resolvedCoalesceSchedulerThreadsMin();
        int maxWorkers = properties.resolvedCoalesceSchedulerThreadsMax();
        int target = Math.min(maxWorkers, Math.max(minWorkers, schedulerScaler.targetWorkers()));
        ThreadPoolResize.apply(scheduler, target, target);
    }

    public void recordUpdate(String path, String variableName, DataRecord value) {
        recordUpdate(path, variableName, value, null);
    }

    public void recordUpdate(String path, String variableName, DataRecord value, Instant observedAt) {
        String coalesceKey = resolveCoalesceKey(path, variableName, value);
        if (!properties.isCoalesceEnabled()) {
            publishIfChanged(path, variableName, value, coalesceKey, observedAt);
            return;
        }
        if (suppressUnchangedSample(path, variableName, value, coalesceKey)) {
            return;
        }
        if (!properties.isEnabled()) {
            publishIfChanged(path, variableName, value, coalesceKey, observedAt);
            return;
        }
        ensureSchedulerStarted();
        pending.put(coalesceKey, new PendingUpdate(path, variableName, value, observedAt));
        adjustSchedulerWorkers();
        scheduleFlushForLane(coalesceKey, path);
    }

    public void flushNow() {
        telemetryIngressDispatcher.flushNow();
        flushAll();
        gatewayIngressDispatch.flushNow();
    }

    /**
     * Publishes a batch-coalesced update (ingress queue path). Skips the coalescer pending map because
     * {@link TelemetryIngressDispatcher} already merged lanes.
     */
    public void publishCoalescedUpdate(String path, String variableName, DataRecord value, Instant observedAt) {
        publishCoalescedBatch(List.of(new CoalescedTelemetryUpdate(path, variableName, value, observedAt)));
    }

    /**
     * Publishes a drained ingress batch. Historian-only {@code TELEMETRY_ONLY} lanes are merged into one
     * enqueue on the fast path; gateway and bus lanes are published individually.
     * <p>
     * Skips {@link #valuesEqual(DataRecord, DataRecord)} — {@link TelemetryIngressDispatcher} already
     * last-value-wins coalesced each lane; historian sampling is gated by {@code minIntervalMs}, not payload dedup.
     */
    public void publishCoalescedBatch(List<CoalescedTelemetryUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        List<CoalescedTelemetryUpdate> historianBatch = new ArrayList<>(updates.size());
        for (CoalescedTelemetryUpdate update : updates) {
            String coalesceKey = resolveCoalesceKey(update.path(), update.variableName(), update.value());
            lastPublished.put(coalesceKey, update.value());
            if (gatewayIngressDispatch.tryScheduleDispatch(update.path(), update.variableName(), update.value())) {
                continue;
            }
            if (historianFastPath.isHistorianOnlyEligible(update.path(), update.variableName())) {
                historianBatch.add(update);
                continue;
            }
            if (!historianFastPath.tryPublish(
                    update.path(),
                    update.variableName(),
                    update.value(),
                    update.observedAt()
            )) {
                publicationService.publishVariableChange(update.path(), update.variableName(), update.observedAt());
            }
        }
        if (!historianBatch.isEmpty()) {
            historianFastPath.publishBatch(historianBatch);
        }
    }

    @PreDestroy
    public void shutdown() {
        flushAll();
        if (scheduler != null) {
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
        adjustSchedulerWorkers();
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
        if (suppressUnchangedSample(path, variableName, value, coalesceKey)) {
            return;
        }
        DataRecord last = lastPublished.get(coalesceKey);
        DataRecord previous = policyService.includePreviousValueInEvent(path, variableName) ? last : null;
        DataRecord eventValue = policyService.includePreviousValueInEvent(path, variableName) ? value : null;
        lastPublished.put(coalesceKey, value);
        if (gatewayIngressDispatch.tryScheduleDispatch(path, variableName, value)) {
            return;
        }
        if (historianFastPath.tryPublish(path, variableName, value, observedAt)) {
            return;
        }
        publicationService.publishVariableChange(path, variableName, observedAt, eventValue, previous);
    }

    /**
     * {@link HistorySampleMode#CHANGES_ONLY} suppresses historian churn on duplicate payloads, but
     * {@link com.ispf.server.driver.TelemetryPublishMode#FULL} automation (bindings, alerts) still
     * needs object-change ticks — same rationale as gateway child {@code ALL_VALUES} override.
     */
    private boolean suppressUnchangedSample(
            String path,
            String variableName,
            DataRecord value,
            String coalesceKey
    ) {
        if (policyService.historySampleMode(path, variableName) != HistorySampleMode.CHANGES_ONLY) {
            return false;
        }
        if (!DataRecordValues.equal(lastPublished.get(coalesceKey), value)) {
            return false;
        }
        return !policyService.automationEligible(path, variableName);
    }

    private String resolveCoalesceKey(String path, String variableName, DataRecord value) {
        return TelemetryIngressCoalesceKey.laneKey(path, variableName, value, policyService.ingressPayloadLanes(path));
    }

    static String coalesceKey(String path, String variableName, DataRecord value) {
        return coalesceKey(path, variableName, value, false);
    }

    static String coalesceKey(String path, String variableName, DataRecord value, boolean ingressPayloadLanes) {
        return TelemetryIngressCoalesceKey.laneKey(path, variableName, value, ingressPayloadLanes);
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
