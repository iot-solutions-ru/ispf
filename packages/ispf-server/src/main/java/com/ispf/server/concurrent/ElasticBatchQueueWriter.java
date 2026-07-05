package com.ispf.server.concurrent;

import com.ispf.driver.ingress.ElasticWorkerScaler;
import com.ispf.driver.ingress.IngressElasticSettings;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Elastic multi-worker batch drain for bounded audit / persistence queues.
 */
public final class ElasticBatchQueueWriter<T> {

    private static final Logger log = LoggerFactory.getLogger(ElasticBatchQueueWriter.class);

    @FunctionalInterface
    public interface BatchConsumer<T> {
        void accept(List<T> batch);
    }

    private final IngressElasticSettings settings;
    private final int batchSize;
    private final long flushIntervalMs;
    private final String workerThreadName;
    private final BatchConsumer<T> batchConsumer;

    private BlockingQueue<T> queue;
    private ExecutorService workers;
    private ElasticWorkerScaler scaler;
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private volatile boolean running;

    public ElasticBatchQueueWriter(
            IngressElasticSettings settings,
            int batchSize,
            long flushIntervalMs,
            String workerThreadName,
            BatchConsumer<T> batchConsumer
    ) {
        this.settings = settings;
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(1, flushIntervalMs);
        this.workerThreadName = workerThreadName;
        this.batchConsumer = batchConsumer;
    }

    public void start(int queueCapacity) {
        queue = new LinkedBlockingQueue<>(Math.max(1, queueCapacity));
        running = true;
        if (settings.enabled()) {
            scaler = new ElasticWorkerScaler(
                    settings.resolvedMinWorkers(),
                    settings.resolvedMaxWorkers(),
                    settings.scaleUpQueueThreshold(),
                    settings.scaleDownSteps()
            );
        }
        workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, workerThreadName);
            thread.setDaemon(true);
            return thread;
        });
        int initial = settings.enabled() ? settings.resolvedMinWorkers() : settings.resolvedMaxWorkers();
        for (int i = 0; i < initial; i++) {
            spawnWorker();
        }
        log.info(
                "{} started (queueCapacity={}, batchSize={}, workers={}-{}, elastic={})",
                workerThreadName,
                queueCapacity,
                batchSize,
                settings.resolvedMinWorkers(),
                settings.resolvedMaxWorkers(),
                settings.enabled()
        );
    }

    public boolean isRunning() {
        return running && queue != null;
    }

    public boolean offer(T item) {
        if (queue == null) {
            return false;
        }
        boolean accepted = queue.offer(item);
        if (accepted) {
            adjustWorkers();
        }
        return accepted;
    }

    public int pendingCount() {
        return queue != null ? queue.size() : 0;
    }

    public AtomicInteger activeWorkers() {
        return activeWorkers;
    }

    private void spawnWorker() {
        if (workers == null || activeWorkers.get() >= settings.resolvedMaxWorkers()) {
            return;
        }
        workers.submit(this::writerLoop);
    }

    private void adjustWorkers() {
        if (scaler == null || !running) {
            return;
        }
        scaler.adjust(pendingCount());
        while (activeWorkers.get() < scaler.targetWorkers()
                && activeWorkers.get() < settings.resolvedMaxWorkers()) {
            spawnWorker();
        }
    }

    private void writerLoop() {
        activeWorkers.incrementAndGet();
        List<T> batch = new ArrayList<>(batchSize);
        long lastFlush = System.nanoTime();
        try {
            while (running || pendingCount() > 0) {
                if (scaler != null && activeWorkers.get() > scaler.targetWorkers() && pendingCount() == 0) {
                    break;
                }
                batch.clear();
                T first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - batch.size());
                }
                long elapsedMs = (System.nanoTime() - lastFlush) / 1_000_000L;
                if (!batch.isEmpty() && (batch.size() >= batchSize || elapsedMs >= flushIntervalMs)) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlush = System.nanoTime();
                } else if (batch.isEmpty()) {
                    adjustWorkers();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }
            activeWorkers.decrementAndGet();
        }
    }

    private void flushBatch(List<T> batch) {
        try {
            batchConsumer.accept(batch);
        } catch (Exception ex) {
            log.warn("{} batch flush failed (size={}): {}", workerThreadName, batch.size(), ex.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (workers != null) {
            workers.shutdownNow();
        }
        drainSync();
    }

    public void drainSync() {
        if (queue == null) {
            return;
        }
        List<T> batch = new ArrayList<>(batchSize);
        while (!queue.isEmpty()) {
            batch.clear();
            queue.drainTo(batch, batchSize);
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }
        }
    }
}
