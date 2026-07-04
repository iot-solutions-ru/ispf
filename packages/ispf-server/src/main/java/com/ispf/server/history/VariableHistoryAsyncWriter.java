package com.ispf.server.history;

import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.persistence.entity.VariableSampleEntity;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.driver.ingress.ElasticWorkerScaler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class VariableHistoryAsyncWriter {

    private static final Logger log = LoggerFactory.getLogger(VariableHistoryAsyncWriter.class);

    private final VariableHistoryProperties properties;
    private final VariableHistoryBatchPersister batchPersister;
    private final AutomationMetricsRecorder automationMetricsRecorder;

    private BlockingQueue<VariableSampleEntity> queue;
    private ConcurrentHashMap<String, VariableSampleEntity> overflowCoalesce;
    private ExecutorService workers;
    private ScheduledExecutorService scaleScheduler;
    private ScheduledFuture<?> scaleTask;
    private ElasticWorkerScaler scaler;
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private volatile boolean running;

    public VariableHistoryAsyncWriter(
            VariableHistoryProperties properties,
            VariableHistoryBatchPersister batchPersister,
            AutomationMetricsRecorder automationMetricsRecorder
    ) {
        this.properties = properties;
        this.batchPersister = batchPersister;
        this.automationMetricsRecorder = automationMetricsRecorder;
    }

    @jakarta.annotation.PostConstruct
    void start() {
        if (!properties.isAsyncEnabled()) {
            return;
        }
        queue = new LinkedBlockingQueue<>(properties.getQueueCapacity());
        if (properties.isOverflowCoalesceEnabled()) {
            overflowCoalesce = new ConcurrentHashMap<>();
        }
        running = true;
        if (properties.isElasticWriterEnabled()) {
            scaler = new ElasticWorkerScaler(
                    properties.resolvedWriterThreadsMin(),
                    properties.resolvedWriterThreadsMax(),
                    properties.getElasticScaleUpQueueThreshold(),
                    properties.getElasticScaleDownSteps()
            );
            scaleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "variable-history-scale");
                thread.setDaemon(true);
                return thread;
            });
            scaleTask = scaleScheduler.scheduleAtFixedRate(
                    this::adjustWorkers,
                    properties.getElasticScaleCheckIntervalMs(),
                    properties.getElasticScaleCheckIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        }
        workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "variable-history-writer");
            thread.setDaemon(true);
            return thread;
        });
        int initial = properties.isElasticWriterEnabled()
                ? properties.resolvedWriterThreadsMin()
                : properties.resolvedWriterThreadsMax();
        for (int i = 0; i < initial; i++) {
            spawnWorker();
        }
        automationMetricsRecorder.bindVariableHistoryQueue(queue);
        automationMetricsRecorder.bindVariableHistoryWorkers(activeWorkers);
        log.info(
                "Variable history async writer started (queueCapacity={}, batchSize={}, flushIntervalMs={}, "
                        + "writers={}-{}, elastic={}, overflowCoalesce={})",
                properties.getQueueCapacity(),
                properties.getBatchSize(),
                properties.getFlushIntervalMs(),
                properties.resolvedWriterThreadsMin(),
                properties.resolvedWriterThreadsMax(),
                properties.isElasticWriterEnabled(),
                properties.isOverflowCoalesceEnabled()
        );
    }

    public boolean isAsyncEnabled() {
        return properties.isAsyncEnabled() && queue != null;
    }

    public void enqueue(List<VariableSampleEntity> samples) {
        if (samples.isEmpty()) {
            return;
        }
        if (!isAsyncEnabled()) {
            batchPersister.persistBatch(samples);
            automationMetricsRecorder.recordVariableHistoryFlushed(samples.size());
            return;
        }
        for (VariableSampleEntity sample : samples) {
            enqueueOne(sample);
        }
    }

    private void enqueueOne(VariableSampleEntity sample) {
        if (queue.offer(sample)) {
            maybeScaleUp();
            return;
        }
        if (overflowCoalesce != null) {
            String key = coalesceKey(sample);
            VariableSampleEntity previous = overflowCoalesce.put(key, sample);
            if (previous == null) {
                automationMetricsRecorder.recordVariableHistoryOverflowCoalesced();
            } else {
                automationMetricsRecorder.recordVariableHistoryCoalesced();
            }
            maybeScaleUp();
            return;
        }
        automationMetricsRecorder.recordVariableHistorySyncFallback();
        log.warn(
                "Variable history queue full (capacity={}); sync persist for {} {}.{}",
                properties.getQueueCapacity(),
                sample.getObjectPath(),
                sample.getVariableName(),
                sample.getFieldName()
        );
        batchPersister.persistOne(sample);
        automationMetricsRecorder.recordVariableHistoryFlushed(1);
    }

    private void maybeScaleUp() {
        if (scaler != null && totalPending() >= properties.getElasticScaleUpQueueThreshold()) {
            adjustWorkers();
        }
    }

    private int totalPending() {
        int overflow = overflowCoalesce != null ? overflowCoalesce.size() : 0;
        return queue.size() + overflow;
    }

    public void awaitQueueDrain(long timeout, TimeUnit unit) throws InterruptedException {
        if (queue == null) {
            return;
        }
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadlineNanos) {
            if (totalPending() == 0) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private void spawnWorker() {
        if (workers == null || activeWorkers.get() >= properties.resolvedWriterThreadsMax()) {
            return;
        }
        workers.submit(this::writerLoop);
    }

    private void adjustWorkers() {
        if (scaler == null || !running) {
            return;
        }
        scaler.adjust(totalPending());
        while (activeWorkers.get() < scaler.targetWorkers()
                && activeWorkers.get() < properties.resolvedWriterThreadsMax()) {
            spawnWorker();
        }
    }

    private void writerLoop() {
        activeWorkers.incrementAndGet();
        int batchSize = Math.max(1, properties.getBatchSize());
        long flushIntervalMs = Math.max(1, properties.getFlushIntervalMs());
        try {
            while (running || totalPending() > 0) {
                if (scaler != null && activeWorkers.get() > scaler.targetWorkers() && totalPending() == 0) {
                    break;
                }
                List<VariableSampleEntity> batch = new ArrayList<>(batchSize);
                drainOverflowInto(batch, batchSize);
                VariableSampleEntity first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - batch.size());
                }
                if (!batch.isEmpty()) {
                    flushBatch(batch);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            activeWorkers.decrementAndGet();
        }
    }

    private void drainOverflowInto(List<VariableSampleEntity> batch, int batchSize) {
        if (overflowCoalesce == null || overflowCoalesce.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, VariableSampleEntity>> iterator = overflowCoalesce.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < batchSize) {
            Map.Entry<String, VariableSampleEntity> entry = iterator.next();
            VariableSampleEntity sample = overflowCoalesce.remove(entry.getKey());
            if (sample != null) {
                batch.add(sample);
            }
        }
    }

    private void drainRemaining() {
        if (queue == null) {
            return;
        }
        List<VariableSampleEntity> batch = new ArrayList<>(Math.max(1, properties.getBatchSize()));
        while (totalPending() > 0) {
            batch.clear();
            drainOverflowInto(batch, properties.getBatchSize());
            queue.drainTo(batch, properties.getBatchSize() - batch.size());
            if (!batch.isEmpty()) {
                flushBatch(batch);
            } else {
                break;
            }
        }
    }

    private void flushBatch(List<VariableSampleEntity> batch) {
        try {
            batchPersister.persistBatch(batch);
            automationMetricsRecorder.recordVariableHistoryFlushed(batch.size());
        } catch (Exception ex) {
            log.error("Variable history batch flush failed (size={}); falling back to per-sample sync", batch.size(), ex);
            for (VariableSampleEntity sample : batch) {
                try {
                    batchPersister.persistOne(sample);
                    automationMetricsRecorder.recordVariableHistoryFlushed(1);
                } catch (Exception singleEx) {
                    log.error(
                            "Variable history sync fallback failed for {} {}.{}",
                            sample.getObjectPath(),
                            sample.getVariableName(),
                            sample.getFieldName(),
                            singleEx
                    );
                }
            }
        }
    }

    private static String coalesceKey(VariableSampleEntity sample) {
        return sample.getObjectPath() + "|" + sample.getVariableName() + "|" + sample.getFieldName();
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
        if (workers != null) {
            workers.shutdownNow();
        }
        drainRemaining();
    }
}
