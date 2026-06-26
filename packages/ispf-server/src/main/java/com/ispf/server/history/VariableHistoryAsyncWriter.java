package com.ispf.server.history;

import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.persistence.entity.VariableSampleEntity;
import com.ispf.server.platform.AutomationMetricsRecorder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class VariableHistoryAsyncWriter {

    private static final Logger log = LoggerFactory.getLogger(VariableHistoryAsyncWriter.class);

    private final VariableHistoryProperties properties;
    private final VariableHistoryBatchPersister batchPersister;
    private final AutomationMetricsRecorder automationMetricsRecorder;

    private BlockingQueue<VariableSampleEntity> queue;
    private ExecutorService worker;
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
        running = true;
        int writerThreads = Math.max(1, properties.getWriterThreads());
        worker = Executors.newFixedThreadPool(writerThreads, runnable -> {
            Thread thread = new Thread(runnable, "variable-history-writer");
            thread.setDaemon(true);
            return thread;
        });
        for (int i = 0; i < writerThreads; i++) {
            worker.submit(this::writerLoop);
        }
        automationMetricsRecorder.bindVariableHistoryQueue(queue);
        log.info(
                "Variable history async writer started (queueCapacity={}, batchSize={}, flushIntervalMs={}, writerThreads={})",
                properties.getQueueCapacity(),
                properties.getBatchSize(),
                properties.getFlushIntervalMs(),
                writerThreads
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

    public void awaitQueueDrain(long timeout, TimeUnit unit) throws InterruptedException {
        if (queue == null) {
            return;
        }
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadlineNanos) {
            if (queue.isEmpty()) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private void writerLoop() {
        int batchSize = Math.max(1, properties.getBatchSize());
        long flushIntervalMs = Math.max(1, properties.getFlushIntervalMs());
        while (running || !queue.isEmpty()) {
            try {
                List<VariableSampleEntity> batch = new ArrayList<>(batchSize);
                VariableSampleEntity first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - 1);
                }
                if (!batch.isEmpty()) {
                    flushBatch(batch);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        drainRemaining();
    }

    private void drainRemaining() {
        if (queue == null || queue.isEmpty()) {
            return;
        }
        List<VariableSampleEntity> batch = new ArrayList<>(Math.max(1, properties.getBatchSize()));
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            flushBatch(batch);
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

    @PreDestroy
    void shutdown() {
        running = false;
        if (worker == null) {
            return;
        }
        worker.shutdown();
        try {
            if (!worker.awaitTermination(15, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
        drainRemaining();
    }
}
