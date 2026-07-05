package com.ispf.driver.ingress;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

/**
 * Last-value-wins ingress buffer for driver protocol callbacks and poll hand-off.
 * <p>
 * Producers enqueue quickly; workers drain batches into the platform without blocking I/O threads
 * or growing an unbounded FIFO queue.
 */
public final class DriverIngressBuffer<K, V> {

    private final int capacity;
    private final BiConsumer<K, V> handler;
    private final boolean eagerDrain;
    private final IngressElasticSettings elastic;
    private final ConcurrentHashMap<K, V> pendingByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, AtomicBoolean> laneDrainScheduled = new ConcurrentHashMap<>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicInteger coalescedTotal = new AtomicInteger();
    private final AtomicInteger evictedTotal = new AtomicInteger();
    private final AtomicInteger activeWorkers = new AtomicInteger();

    private volatile boolean running = true;
    private ExecutorService workers;
    private ElasticWorkerScaler scaler;
    private ScheduledExecutorService scaleScheduler;
    private ScheduledFuture<?> scaleTask;
    private volatile Thread drainWaiter;

    public DriverIngressBuffer(int workerThreads, int capacity, BiConsumer<K, V> handler) {
        this(workerThreads, capacity, handler, "driver-ingress-worker", false);
    }

    public DriverIngressBuffer(int workerThreads, int capacity, BiConsumer<K, V> handler, String threadNamePrefix) {
        this(workerThreads, capacity, handler, threadNamePrefix, false);
    }

    /**
     * @param eagerDrain when true, schedules per-lane drain on submit (no worker park loop) for
     *                   high-rate single-lane MQTT/telemetry paths
     */
    public DriverIngressBuffer(
            int workerThreads,
            int capacity,
            BiConsumer<K, V> handler,
            String threadNamePrefix,
            boolean eagerDrain
    ) {
        this(IngressElasticSettings.fixed(workerThreads), capacity, handler, threadNamePrefix, eagerDrain);
    }

    public DriverIngressBuffer(
            IngressElasticSettings elastic,
            int capacity,
            BiConsumer<K, V> handler,
            String threadNamePrefix,
            boolean eagerDrain
    ) {
        this.capacity = Math.max(1, capacity);
        this.handler = handler;
        this.eagerDrain = eagerDrain;
        this.elastic = elastic;
        startWorkers(threadNamePrefix);
    }

    private void startWorkers(String threadNamePrefix) {
        int minWorkers = elastic.resolvedMinWorkers();
        int maxWorkers = elastic.resolvedMaxWorkers();
        if (elastic.enabled()) {
            scaler = new ElasticWorkerScaler(
                    minWorkers,
                    maxWorkers,
                    elastic.scaleUpQueueThreshold(),
                    elastic.scaleDownSteps()
            );
            scaleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, threadNamePrefix + "-scale");
                thread.setDaemon(true);
                return thread;
            });
            scaleTask = scaleScheduler.scheduleAtFixedRate(
                    this::adjustWorkers,
                    elastic.scaleCheckIntervalMs(),
                    elastic.scaleCheckIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        }
        if (eagerDrain) {
            workers = new java.util.concurrent.ThreadPoolExecutor(
                    minWorkers,
                    maxWorkers,
                    60L,
                    TimeUnit.MILLISECONDS,
                    new java.util.concurrent.SynchronousQueue<>(),
                    runnable -> {
                        Thread thread = new Thread(runnable, threadNamePrefix);
                        thread.setDaemon(true);
                        return thread;
                    },
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
            );
            return;
        }
        workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, threadNamePrefix);
            thread.setDaemon(true);
            return thread;
        });
        int initial = elastic.enabled() ? minWorkers : maxWorkers;
        for (int i = 0; i < initial; i++) {
            spawnWorker();
        }
    }

    public void submit(K key, V value) {
        if (!running) {
            return;
        }
        V previous = pendingByKey.put(key, value);
        if (previous == null) {
            int size = pendingCount.incrementAndGet();
            if (size > capacity) {
                V removed = pendingByKey.remove(key);
                if (removed != null && pendingCount.decrementAndGet() >= 0) {
                    evictedTotal.incrementAndGet();
                }
                handler.accept(key, value);
                return;
            }
        } else {
            coalescedTotal.incrementAndGet();
        }
        if (eagerDrain) {
            scheduleLaneDrain(key);
        } else {
            unparkDrainWaiter();
        }
        maybeScaleUp();
    }

    public void shutdown() {
        running = false;
        if (scaleTask != null) {
            scaleTask.cancel(false);
        }
        if (scaleScheduler != null) {
            scaleScheduler.shutdownNow();
        }
        unparkDrainWaiter();
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
        drainAllRemaining();
    }

    /** Drains pending lanes synchronously (for explicit poll/write API semantics). */
    public void flushNow() {
        drainAllRemaining();
    }

    public int coalescedTotal() {
        return coalescedTotal.get();
    }

    public int evictedTotal() {
        return evictedTotal.get();
    }

    public int activeWorkers() {
        return activeWorkers.get();
    }

    public int pendingCount() {
        return pendingCount.get();
    }

    private void maybeScaleUp() {
        if (scaler != null && pendingCount.get() >= elastic.scaleUpQueueThreshold()) {
            adjustWorkers();
        }
    }

    private void adjustWorkers() {
        if (scaler == null || !running) {
            return;
        }
        if (eagerDrain && workers instanceof java.util.concurrent.ThreadPoolExecutor pool) {
            scaler.adjust(pendingCount.get());
            int target = scaler.targetWorkers();
            pool.setCorePoolSize(target);
            pool.setMaximumPoolSize(elastic.resolvedMaxWorkers());
            while (pool.getPoolSize() < target && pool.getPoolSize() < pool.getMaximumPoolSize()) {
                pool.prestartCoreThread();
            }
            return;
        }
        scaler.adjust(pendingCount.get());
        while (activeWorkers.get() < scaler.targetWorkers()
                && activeWorkers.get() < elastic.resolvedMaxWorkers()) {
            spawnWorker();
        }
    }

    private void spawnWorker() {
        if (workers == null || activeWorkers.get() >= elastic.resolvedMaxWorkers()) {
            return;
        }
        workers.submit(this::workerLoop);
    }

    private void scheduleLaneDrain(K key) {
        AtomicBoolean scheduled = laneDrainScheduled.computeIfAbsent(key, ignored -> new AtomicBoolean(false));
        if (scheduled.compareAndSet(false, true)) {
            workers.execute(() -> drainLane(key, scheduled));
        }
    }

    private void drainLane(K key, AtomicBoolean scheduled) {
        try {
            while (running) {
                V payload = pendingByKey.remove(key);
                if (payload == null) {
                    return;
                }
                pendingCount.decrementAndGet();
                handler.accept(key, payload);
                if (!pendingByKey.containsKey(key)) {
                    return;
                }
            }
        } finally {
            scheduled.set(false);
            if (running && pendingByKey.containsKey(key)) {
                scheduleLaneDrain(key);
            }
        }
    }

    private void workerLoop() {
        activeWorkers.incrementAndGet();
        try {
            while (running || pendingCount.get() > 0) {
                if (scaler != null && activeWorkers.get() > scaler.targetWorkers() && pendingCount.get() == 0) {
                    break;
                }
                List<Entry> batch = drainBatch(64);
                if (batch.isEmpty()) {
                    drainWaiter = Thread.currentThread();
                    LockSupport.parkNanos(250_000L);
                    continue;
                }
                for (Entry entry : batch) {
                    handler.accept(entry.key, entry.value);
                }
            }
        } finally {
            activeWorkers.decrementAndGet();
        }
    }

    private List<Entry> drainBatch(int maxBatch) {
        List<Entry> batch = new ArrayList<>(Math.min(maxBatch, Math.max(0, pendingCount.get())));
        Iterator<Map.Entry<K, V>> iterator = pendingByKey.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < maxBatch) {
            Map.Entry<K, V> entry = iterator.next();
            V payload = pendingByKey.remove(entry.getKey());
            if (payload != null) {
                pendingCount.decrementAndGet();
                batch.add(new Entry(entry.getKey(), payload));
            }
        }
        return batch;
    }

    private void drainAllRemaining() {
        List<Entry> batch;
        do {
            batch = drainBatch(Math.max(256, capacity));
            for (Entry entry : batch) {
                handler.accept(entry.key, entry.value);
            }
        } while (!batch.isEmpty());
    }

    private void unparkDrainWaiter() {
        Thread waiter = drainWaiter;
        if (waiter != null) {
            LockSupport.unpark(waiter);
        }
    }

    private final class Entry {
        private final K key;
        private final V value;

        private Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
