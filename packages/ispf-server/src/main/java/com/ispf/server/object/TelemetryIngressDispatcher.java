package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.driver.ingress.ElasticWorkerScaler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Elastic ingress queue for high-rate driver telemetry.
 * <p>
 * Last-value-wins per coalesce lane — under overload, newer samples replace pending lanes instead of
 * blocking MQTT callback threads or growing an unbounded FIFO queue under overload.
 */
@Service
public class TelemetryIngressDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngressDispatcher.class);

    private final RuntimeTelemetryProperties properties;
    private final RuntimeTelemetryCoalescer coalescer;
    private final AutomationMetricsRecorder automationMetricsRecorder;

    private final ConcurrentHashMap<String, PendingUpdate> pendingByKey = new ConcurrentHashMap<>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private volatile boolean running;
    private ExecutorService workers;
    private ScheduledExecutorService scaleScheduler;
    private ScheduledFuture<?> scaleTask;
    private ElasticWorkerScaler scaler;
    private final AtomicInteger activeWorkers = new AtomicInteger();

    public TelemetryIngressDispatcher(
            RuntimeTelemetryProperties properties,
            @Lazy RuntimeTelemetryCoalescer coalescer,
            AutomationMetricsRecorder automationMetricsRecorder
    ) {
        this.properties = properties;
        this.coalescer = coalescer;
        this.automationMetricsRecorder = automationMetricsRecorder;
    }

    @jakarta.annotation.PostConstruct
    void start() {
        if (!properties.isIngressQueueEnabled()) {
            return;
        }
        running = true;
        int minWorkers = properties.resolvedIngressWorkerThreadsMin();
        int maxWorkers = properties.resolvedIngressWorkerThreadsMax();
        if (properties.isIngressElasticWorkers()) {
            scaler = new ElasticWorkerScaler(
                    minWorkers,
                    maxWorkers,
                    properties.getIngressScaleUpQueueThreshold(),
                    properties.getIngressScaleDownSteps()
            );
            AtomicInteger scaleIndex = new AtomicInteger();
            scaleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "telemetry-ingress-scale");
                thread.setDaemon(true);
                return thread;
            });
            scaleTask = scaleScheduler.scheduleAtFixedRate(
                    this::adjustWorkers,
                    properties.getIngressScaleCheckIntervalMs(),
                    properties.getIngressScaleCheckIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        }
        workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "telemetry-ingress-worker");
            thread.setDaemon(true);
            return thread;
        });
        int initial = properties.isIngressElasticWorkers() ? minWorkers : maxWorkers;
        for (int i = 0; i < initial; i++) {
            spawnWorker();
        }
        automationMetricsRecorder.bindTelemetryIngressPending(pendingCount);
        automationMetricsRecorder.bindTelemetryIngressWorkers(activeWorkers);
        log.info(
                "Telemetry ingress queue started (capacity={}, workers={}-{}, elastic={}, drainBatch={})",
                properties.getIngressQueueCapacity(),
                minWorkers,
                maxWorkers,
                properties.isIngressElasticWorkers(),
                properties.getIngressDrainBatchSize()
        );
    }

    public boolean isEnabled() {
        return properties.isIngressQueueEnabled() && running;
    }

    public void submit(String path, String variableName, DataRecord value, Instant observedAt) {
        if (!isEnabled()) {
            coalescer.recordUpdate(path, variableName, value, observedAt);
            return;
        }
        String key = RuntimeTelemetryCoalescer.coalesceKey(path, variableName, value, false);
        PendingUpdate update = new PendingUpdate(path, variableName, value, observedAt);
        PendingUpdate previous = pendingByKey.put(key, update);
        if (previous == null) {
            int size = pendingCount.incrementAndGet();
            if (size > properties.getIngressQueueCapacity()) {
                pendingByKey.remove(key, update);
                if (pendingCount.decrementAndGet() >= 0) {
                    automationMetricsRecorder.recordTelemetryIngressLaneEvicted();
                }
                coalescer.publishCoalescedUpdate(path, variableName, value, observedAt);
                return;
            }
        } else {
            automationMetricsRecorder.recordTelemetryIngressCoalesced();
        }
        if (properties.isIngressElasticWorkers() && pendingCount.get() >= properties.getIngressScaleUpQueueThreshold()) {
            adjustWorkers();
        }
        LockSupport.unpark(drainWaiter);
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (scaleTask != null) {
            scaleTask.cancel(false);
        }
        if (scaleScheduler != null) {
            scaleScheduler.shutdownNow();
        }
        LockSupport.unpark(drainWaiter);
        if (workers != null) {
            workers.shutdownNow();
        }
        drainAllRemaining();
    }

    /** For tests. */
    void flushNow() {
        drainAllRemaining();
    }

    private volatile Thread drainWaiter;

    private void spawnWorker() {
        if (workers == null || activeWorkers.get() >= properties.resolvedIngressWorkerThreadsMax()) {
            return;
        }
        workers.submit(this::workerLoop);
    }

    private void adjustWorkers() {
        if (scaler == null || !running) {
            return;
        }
        scaler.adjust(pendingCount.get());
        while (activeWorkers.get() < scaler.targetWorkers()
                && activeWorkers.get() < properties.resolvedIngressWorkerThreadsMax()) {
            spawnWorker();
        }
    }

    private void workerLoop() {
        activeWorkers.incrementAndGet();
        try {
            while (running || pendingCount.get() > 0) {
                if (scaler != null && activeWorkers.get() > scaler.targetWorkers() && pendingCount.get() == 0) {
                    break;
                }
                List<PendingUpdate> drained = drainBatch(properties.getIngressDrainBatchSize());
                if (drained.isEmpty()) {
                    drainWaiter = Thread.currentThread();
                    LockSupport.parkNanos(250_000L);
                    continue;
                }
                List<CoalescedTelemetryUpdate> batch = new ArrayList<>(drained.size());
                for (PendingUpdate update : drained) {
                    batch.add(new CoalescedTelemetryUpdate(
                            update.path(),
                            update.variableName(),
                            update.value(),
                            update.observedAt()
                    ));
                }
                coalescer.publishCoalescedBatch(batch);
            }
        } finally {
            activeWorkers.decrementAndGet();
        }
    }

    private List<PendingUpdate> drainBatch(int maxBatch) {
        List<PendingUpdate> batch = new ArrayList<>(Math.min(maxBatch, Math.max(0, pendingCount.get())));
        Iterator<Map.Entry<String, PendingUpdate>> iterator = pendingByKey.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < maxBatch) {
            Map.Entry<String, PendingUpdate> entry = iterator.next();
            PendingUpdate removed = pendingByKey.remove(entry.getKey());
            if (removed != null) {
                pendingCount.decrementAndGet();
                batch.add(removed);
            }
        }
        return batch;
    }

    private void drainAllRemaining() {
        List<PendingUpdate> drained;
        do {
            drained = drainBatch(Math.max(256, properties.getIngressDrainBatchSize()));
            if (drained.isEmpty()) {
                break;
            }
            List<CoalescedTelemetryUpdate> batch = new ArrayList<>(drained.size());
            for (PendingUpdate update : drained) {
                batch.add(new CoalescedTelemetryUpdate(
                        update.path(),
                        update.variableName(),
                        update.value(),
                        update.observedAt()
                ));
            }
            coalescer.publishCoalescedBatch(batch);
        } while (!drained.isEmpty());
    }

    private record PendingUpdate(String path, String variableName, DataRecord value, Instant observedAt) {}
}
