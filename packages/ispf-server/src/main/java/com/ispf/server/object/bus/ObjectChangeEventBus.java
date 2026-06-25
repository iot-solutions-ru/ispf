package com.ispf.server.object.bus;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.platform.AutomationObservationSupport;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async dispatcher for {@link ObjectChangeEvent} handlers.
 *
 * <p>When {@link ObjectChangeProperties#isSplitLanesEnabled()} is true, two lanes isolate
 * high-volume telemetry history from automation reactions:
 * <ul>
 *   <li><b>Telemetry lane</b> — {@code event.telemetry()==true} events; handlers with
 *       {@link ObjectChangeAsyncHandler#lane()} {@link ObjectChangeLane#TELEMETRY}
 *       (typically {@code VariableHistoryListener}).</li>
 *   <li><b>Automation lane</b> — all events; handlers with lane {@link ObjectChangeLane#AUTOMATION}
 *       (alerts, workflows, correlator, SQL bindings).</li>
 * </ul>
 *
 * <p><b>Coalescing with {@link com.ispf.server.object.RuntimeTelemetryCoalescer}:</b>
 * {@code RuntimeTelemetryCoalescer} is the first stage — it merges rapid variable value changes
 * before publishing {@code ObjectChangeEvent} with {@code telemetry=true}. When
 * {@link ObjectChangeProperties#isCoalesceTelemetryUpdates()} is also enabled, the bus applies a
 * second coalesce (50 ms window) only on the telemetry lane enqueue path. The automation lane
 * never bus-coalesces, so alert/workflow handlers still see every published event. Disable
 * {@code coalesce-telemetry-updates} when {@code runtime-telemetry.enabled=true} to avoid redundant
 * double coalescing on the history path.
 */
@Component
public class ObjectChangeEventBus {

    private static final Logger log = LoggerFactory.getLogger(ObjectChangeEventBus.class);

    private final ObjectChangeProperties properties;
    private final List<ObjectChangeAsyncHandler> handlers;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final AutomationObservationSupport automationObservationSupport;

    private Lane unifiedLane;
    private Lane telemetryLane;
    private Lane automationLane;
    private ScheduledExecutorService coalesceScheduler;
    private volatile boolean running;
    private final ConcurrentHashMap<String, ObjectChangeEvent> telemetryCoalesce = new ConcurrentHashMap<>();
    private volatile boolean coalesceFlushScheduled;

    public ObjectChangeEventBus(
            ObjectChangeProperties properties,
            List<ObjectChangeAsyncHandler> handlers,
            AutomationMetricsRecorder automationMetricsRecorder,
            AutomationObservationSupport automationObservationSupport
    ) {
        this.properties = properties;
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(ObjectChangeAsyncHandler::order))
                .toList();
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.automationObservationSupport = automationObservationSupport;
    }

    @jakarta.annotation.PostConstruct
    void start() {
        if (!properties.isAsyncEnabled()) {
            return;
        }
        running = true;
        if (properties.isSplitLanesEnabled()) {
            telemetryLane = new Lane(
                    ObjectChangeLane.TELEMETRY,
                    properties.getTelemetryQueueCapacity(),
                    properties.resolvedTelemetryWorkerThreadsMin(),
                    properties.resolvedTelemetryWorkerThreadsMax(),
                    properties.isElasticWorkersEnabled(),
                    properties.getElasticScaleUpQueueThreshold(),
                    properties.getElasticScaleDownSteps(),
                    properties.getElasticScaleCheckIntervalMs(),
                    handlersForLane(ObjectChangeLane.TELEMETRY)
            );
            automationLane = new Lane(
                    ObjectChangeLane.AUTOMATION,
                    properties.getAutomationQueueCapacity(),
                    properties.resolvedAutomationWorkerThreadsMin(),
                    properties.resolvedAutomationWorkerThreadsMax(),
                    properties.isElasticWorkersEnabled(),
                    properties.getElasticScaleUpQueueThreshold(),
                    properties.getElasticScaleDownSteps(),
                    properties.getElasticScaleCheckIntervalMs(),
                    handlersForLane(ObjectChangeLane.AUTOMATION)
            );
            telemetryLane.start();
            automationLane.start();
            automationMetricsRecorder.bindObjectChangeQueue("telemetry", telemetryLane.queue);
            automationMetricsRecorder.bindObjectChangeQueue("automation", automationLane.queue);
            log.info(
                    "Object change event bus started (split lanes: telemetry workers={}-{}, queue={}; "
                            + "automation workers={}-{}, queue={}; elastic={}; handlers={})",
                    properties.resolvedTelemetryWorkerThreadsMin(),
                    properties.resolvedTelemetryWorkerThreadsMax(),
                    properties.getTelemetryQueueCapacity(),
                    properties.resolvedAutomationWorkerThreadsMin(),
                    properties.resolvedAutomationWorkerThreadsMax(),
                    properties.getAutomationQueueCapacity(),
                    properties.isElasticWorkersEnabled(),
                    handlers.size()
            );
        } else {
            unifiedLane = new Lane(
                    ObjectChangeLane.AUTOMATION,
                    properties.getQueueCapacity(),
                    properties.resolvedWorkerThreadsMin(),
                    properties.resolvedWorkerThreadsMax(),
                    properties.isElasticWorkersEnabled(),
                    properties.getElasticScaleUpQueueThreshold(),
                    properties.getElasticScaleDownSteps(),
                    properties.getElasticScaleCheckIntervalMs(),
                    handlers
            );
            unifiedLane.start();
            automationMetricsRecorder.bindObjectChangeQueue("unified", unifiedLane.queue);
            log.info(
                    "Object change event bus started (workers={}-{}, queueCapacity={}, elastic={}, handlers={})",
                    properties.resolvedWorkerThreadsMin(),
                    properties.resolvedWorkerThreadsMax(),
                    properties.getQueueCapacity(),
                    properties.isElasticWorkersEnabled(),
                    handlers.size()
            );
        }
        if (properties.isCoalesceTelemetryUpdates()) {
            coalesceScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "object-change-coalesce");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    public void submit(ObjectChangeEvent event) {
        if (!properties.isAsyncEnabled()) {
            dispatchSync(event);
            return;
        }
        if (properties.isSplitLanesEnabled()) {
            submitSplit(event);
            return;
        }
        submitUnified(event);
    }

    private void submitSplit(ObjectChangeEvent event) {
        if (event.telemetry() && telemetryLane != null && telemetryLane.hasHandlers()) {
            if (shouldBusCoalesceTelemetry(event)) {
                coalesceTelemetry(event, telemetryLane);
            } else {
                telemetryLane.enqueue(event);
            }
        }
        if (automationLane != null && event.automationEligible()) {
            automationLane.enqueue(event);
        }
    }

    private void submitUnified(ObjectChangeEvent event) {
        if (shouldBusCoalesceTelemetry(event)) {
            coalesceTelemetry(event, unifiedLane);
            return;
        }
        unifiedLane.enqueue(event);
    }

    private static boolean shouldCoalesceTelemetry(ObjectChangeEvent event) {
        return event.telemetry()
                && event.type() == ObjectChangeType.VARIABLE_UPDATED
                && event.variableName() != null;
    }

    private boolean shouldBusCoalesceTelemetry(ObjectChangeEvent event) {
        return properties.isCoalesceTelemetryUpdates() && shouldCoalesceTelemetry(event);
    }

    private void coalesceTelemetry(ObjectChangeEvent event, Lane lane) {
        String key = event.path() + '\0' + event.variableName();
        telemetryCoalesce.put(key, event);
        scheduleCoalesceFlush(lane);
    }

    private void scheduleCoalesceFlush(Lane lane) {
        if (coalesceScheduler == null || coalesceFlushScheduled) {
            return;
        }
        coalesceFlushScheduled = true;
        coalesceScheduler.schedule(() -> {
            coalesceFlushScheduled = false;
            flushCoalesce(lane);
        }, 50, TimeUnit.MILLISECONDS);
    }

    private void flushCoalesce(Lane lane) {
        List<ObjectChangeEvent> pending = new ArrayList<>(telemetryCoalesce.values());
        telemetryCoalesce.clear();
        for (ObjectChangeEvent event : pending) {
            lane.enqueue(event);
        }
    }

    private void dispatchSync(ObjectChangeEvent event) {
        for (ObjectChangeAsyncHandler handler : handlers) {
            invokeHandler(handler, event, ObjectChangeLane.AUTOMATION);
        }
        automationMetricsRecorder.recordObjectChangeProcessed();
    }

    private List<ObjectChangeAsyncHandler> handlersForLane(ObjectChangeLane lane) {
        return handlers.stream()
                .filter(handler -> handler.lane() == lane)
                .toList();
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (unifiedLane != null) {
            unifiedLane.shutdown();
        }
        if (telemetryLane != null) {
            telemetryLane.shutdown();
        }
        if (automationLane != null) {
            automationLane.shutdown();
        }
        if (coalesceScheduler != null) {
            coalesceScheduler.shutdownNow();
        }
    }

    public void applyRuntimeTuning() {
        if (unifiedLane != null) {
            unifiedLane.syncElasticConfig();
        }
        if (telemetryLane != null) {
            telemetryLane.syncElasticConfig();
        }
        if (automationLane != null) {
            automationLane.syncElasticConfig();
        }
    }

    private void invokeHandler(ObjectChangeAsyncHandler handler, ObjectChangeEvent event, ObjectChangeLane lane) {
        automationObservationSupport.observeObjectChangeHandler(
                handler.getClass().getSimpleName(),
                lane,
                event,
                () -> {
                    try {
                        handler.handle(event);
                    } catch (Exception ex) {
                        log.error(
                                "Object change handler {} failed for {}",
                                handler.getClass().getSimpleName(),
                                event.path(),
                                ex
                        );
                    }
                }
        );
    }

    private final class Lane {
        private final ObjectChangeLane id;
        private final int queueCapacity;
        private volatile int minWorkers;
        private volatile int maxWorkers;
        private final boolean elasticWorkers;
        private final ObjectChangeWorkerScaler scaler;
        private final BlockingQueue<ObjectChangeEvent> queue;
        private final ExecutorService workers;
        private final List<ObjectChangeAsyncHandler> laneHandlers;
        private final AtomicInteger activeWorkers = new AtomicInteger(0);
        private volatile int scaleUpQueueThreshold;
        private final int scaleCheckIntervalMs;
        private final int scaleDownSteps;
        private ScheduledExecutorService scaleScheduler;

        private Lane(
                ObjectChangeLane id,
                int queueCapacity,
                int minWorkers,
                int maxWorkers,
                boolean elasticWorkers,
                int scaleUpQueueThreshold,
                int scaleDownSteps,
                int scaleCheckIntervalMs,
                List<ObjectChangeAsyncHandler> laneHandlers
        ) {
            this.id = id;
            this.queueCapacity = queueCapacity;
            this.minWorkers = minWorkers;
            this.maxWorkers = maxWorkers;
            this.elasticWorkers = elasticWorkers;
            this.scaler = elasticWorkers
                    ? new ObjectChangeWorkerScaler(minWorkers, maxWorkers, scaleUpQueueThreshold, scaleDownSteps)
                    : null;
            this.queue = new LinkedBlockingQueue<>(queueCapacity);
            this.laneHandlers = laneHandlers;
            this.scaleUpQueueThreshold = scaleUpQueueThreshold;
            this.scaleCheckIntervalMs = scaleCheckIntervalMs;
            this.scaleDownSteps = scaleDownSteps;
            this.workers = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "object-change-bus-" + id.name().toLowerCase());
                thread.setDaemon(true);
                return thread;
            });
        }

        private void start() {
            int initial = elasticWorkers ? minWorkers : maxWorkers;
            for (int i = 0; i < initial; i++) {
                spawnWorker();
            }
            automationMetricsRecorder.bindObjectChangeWorkers(id.name().toLowerCase(), activeWorkers);
            if (elasticWorkers) {
                scaleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "object-change-bus-scale-" + id.name().toLowerCase());
                    thread.setDaemon(true);
                    return thread;
                });
                scaleScheduler.scheduleAtFixedRate(
                        this::adjustWorkers,
                        scaleCheckIntervalMs,
                        scaleCheckIntervalMs,
                        TimeUnit.MILLISECONDS
                );
            }
        }

        private void adjustWorkers() {
            if (!running || scaler == null) {
                return;
            }
            scaler.adjust(queue.size());
            while (activeWorkers.get() < scaler.targetWorkers() && activeWorkers.get() < maxWorkers) {
                spawnWorker();
            }
        }

        private void spawnWorker() {
            if (activeWorkers.get() >= maxWorkers) {
                return;
            }
            workers.submit(this::workerLoop);
        }

        private boolean hasHandlers() {
            return !laneHandlers.isEmpty();
        }

        private void enqueue(ObjectChangeEvent event) {
            if (!queue.offer(event)) {
                automationMetricsRecorder.recordObjectChangeQueueDropped();
                log.warn(
                        "Object change {} queue full (capacity={}); processing on publisher thread for {}",
                        id.name().toLowerCase(),
                        queueCapacity,
                        event.path()
                );
                dispatch(event);
                return;
            }
            if (elasticWorkers && queue.size() >= scaleUpQueueThreshold) {
                adjustWorkers();
            }
        }

        private void workerLoop() {
            activeWorkers.incrementAndGet();
            try {
                while (running) {
                    if (shouldWorkerExit()) {
                        break;
                    }
                    ObjectChangeEvent event = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        dispatch(event);
                    } else if (shouldWorkerExit()) {
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                activeWorkers.decrementAndGet();
            }
        }

        private boolean shouldWorkerExit() {
            return elasticWorkers
                    && scaler != null
                    && activeWorkers.get() > scaler.targetWorkers()
                    && queue.isEmpty();
        }

        private void dispatch(ObjectChangeEvent event) {
            for (ObjectChangeAsyncHandler handler : laneHandlers) {
                invokeHandler(handler, event, id);
            }
            automationMetricsRecorder.recordObjectChangeProcessed();
        }

        private void syncElasticConfig() {
            if (!elasticWorkers || scaler == null) {
                return;
            }
            int resolvedMin = resolveMinWorkers();
            int resolvedMax = resolveMaxWorkers();
            minWorkers = resolvedMin;
            maxWorkers = resolvedMax;
            scaleUpQueueThreshold = properties.getElasticScaleUpQueueThreshold();
            scaler.reconfigure(
                    resolvedMin,
                    resolvedMax,
                    scaleUpQueueThreshold,
                    properties.getElasticScaleDownSteps()
            );
            adjustWorkers();
        }

        private int resolveMinWorkers() {
            if (id == ObjectChangeLane.TELEMETRY) {
                return properties.resolvedTelemetryWorkerThreadsMin();
            }
            if (properties.isSplitLanesEnabled()) {
                return properties.resolvedAutomationWorkerThreadsMin();
            }
            return properties.resolvedWorkerThreadsMin();
        }

        private int resolveMaxWorkers() {
            if (id == ObjectChangeLane.TELEMETRY) {
                return properties.resolvedTelemetryWorkerThreadsMax();
            }
            if (properties.isSplitLanesEnabled()) {
                return properties.resolvedAutomationWorkerThreadsMax();
            }
            return properties.resolvedWorkerThreadsMax();
        }

        private void shutdown() {
            if (scaleScheduler != null) {
                scaleScheduler.shutdownNow();
            }
            workers.shutdownNow();
        }
    }
}
