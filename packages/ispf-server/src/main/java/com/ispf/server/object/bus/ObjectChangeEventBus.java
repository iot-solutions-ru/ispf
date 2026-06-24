package com.ispf.server.object.bus;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ObjectChangeEventBus {

    private static final Logger log = LoggerFactory.getLogger(ObjectChangeEventBus.class);

    private final ObjectChangeProperties properties;
    private final List<ObjectChangeAsyncHandler> handlers;
    private final Optional<MeterRegistry> meterRegistry;

    private BlockingQueue<ObjectChangeEvent> queue;
    private ExecutorService workers;
    private ScheduledExecutorService coalesceScheduler;
    private volatile boolean running;
    private final ConcurrentHashMap<String, ObjectChangeEvent> telemetryCoalesce = new ConcurrentHashMap<>();
    private volatile boolean coalesceFlushScheduled;
    private final AtomicLong processedEvents = new AtomicLong();

    public ObjectChangeEventBus(
            ObjectChangeProperties properties,
            List<ObjectChangeAsyncHandler> handlers,
            Optional<MeterRegistry> meterRegistry
    ) {
        this.properties = properties;
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(ObjectChangeAsyncHandler::order))
                .toList();
        this.meterRegistry = meterRegistry;
    }

    @jakarta.annotation.PostConstruct
    void start() {
        if (!properties.isAsyncEnabled()) {
            return;
        }
        queue = new LinkedBlockingQueue<>(properties.getQueueCapacity());
        running = true;
        workers = Executors.newFixedThreadPool(
                properties.getWorkerThreads(),
                runnable -> {
                    Thread thread = new Thread(runnable, "object-change-bus");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        for (int i = 0; i < properties.getWorkerThreads(); i++) {
            workers.submit(this::workerLoop);
        }
        if (properties.isCoalesceTelemetryUpdates()) {
            coalesceScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "object-change-coalesce");
                thread.setDaemon(true);
                return thread;
            });
        }
        meterRegistry.ifPresent(registry -> {
            registry.gauge("ispf.object_change.queue.size", Tags.empty(), queue, BlockingQueue::size);
            registry.gauge("ispf.object_change.processed.total", Tags.empty(), processedEvents, AtomicLong::doubleValue);
        });
        log.info(
                "Object change event bus started (workers={}, queueCapacity={}, handlers={})",
                properties.getWorkerThreads(),
                properties.getQueueCapacity(),
                handlers.size()
        );
    }

    public void submit(ObjectChangeEvent event) {
        if (!properties.isAsyncEnabled()) {
            dispatch(event);
            return;
        }
        if (properties.isCoalesceTelemetryUpdates()
                && event.telemetry()
                && event.type() == ObjectChangeType.VARIABLE_UPDATED
                && event.variableName() != null) {
            coalesceTelemetry(event);
            return;
        }
        enqueue(event);
    }

    private void coalesceTelemetry(ObjectChangeEvent event) {
        String key = event.path() + '\0' + event.variableName();
        telemetryCoalesce.put(key, event);
        scheduleCoalesceFlush();
    }

    private void scheduleCoalesceFlush() {
        if (coalesceScheduler == null || coalesceFlushScheduled) {
            return;
        }
        coalesceFlushScheduled = true;
        coalesceScheduler.schedule(() -> {
            coalesceFlushScheduled = false;
            flushCoalesce();
        }, 50, TimeUnit.MILLISECONDS);
    }

    private void flushCoalesce() {
        List<ObjectChangeEvent> pending = new ArrayList<>(telemetryCoalesce.values());
        telemetryCoalesce.clear();
        for (ObjectChangeEvent event : pending) {
            enqueue(event);
        }
    }

    private void enqueue(ObjectChangeEvent event) {
        if (queue == null) {
            dispatch(event);
            return;
        }
        if (!queue.offer(event)) {
            log.warn(
                    "Object change queue full (capacity={}); processing on publisher thread for {}",
                    properties.getQueueCapacity(),
                    event.path()
            );
            dispatch(event);
        }
    }

    private void workerLoop() {
        while (running) {
            try {
                ObjectChangeEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    dispatch(event);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void dispatch(ObjectChangeEvent event) {
        for (ObjectChangeAsyncHandler handler : handlers) {
            try {
                handler.handle(event);
            } catch (Exception ex) {
                log.error("Object change handler {} failed for {}", handler.getClass().getSimpleName(), event.path(), ex);
            }
        }
        processedEvents.incrementAndGet();
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (workers != null) {
            workers.shutdownNow();
        }
        if (coalesceScheduler != null) {
            coalesceScheduler.shutdownNow();
        }
    }
}
