package com.ispf.server.event;

import com.ispf.core.object.ObjectEvent;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.persistence.entity.EventHistoryEntity;
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
public class EventJournalAsyncWriter {

    private static final Logger log = LoggerFactory.getLogger(EventJournalAsyncWriter.class);

    record PendingEntry(ObjectEvent event, String payloadJson) {
        EventHistoryEntity toEntity() {
            EventHistoryEntity entity = new EventHistoryEntity();
            entity.setId(event.id());
            entity.setObjectPath(event.objectPath());
            entity.setEventName(event.eventName());
            entity.setLevel(event.level().name());
            entity.setPayloadJson(payloadJson);
            entity.setOccurredAt(event.timestamp());
            return entity;
        }
    }

    private final EventJournalProperties properties;
    private final EventJournalBatchPersister batchPersister;
    private final RecentEventCache recentEventCache;
    private final AutomationMetricsRecorder automationMetricsRecorder;

    private BlockingQueue<PendingEntry> queue;
    private ExecutorService worker;
    private volatile boolean running;

    public EventJournalAsyncWriter(
            EventJournalProperties properties,
            EventJournalBatchPersister batchPersister,
            RecentEventCache recentEventCache,
            AutomationMetricsRecorder automationMetricsRecorder
    ) {
        this.properties = properties;
        this.batchPersister = batchPersister;
        this.recentEventCache = recentEventCache;
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
            Thread thread = new Thread(runnable, "event-journal-writer");
            thread.setDaemon(true);
            return thread;
        });
        for (int i = 0; i < writerThreads; i++) {
            worker.submit(this::writerLoop);
        }
        automationMetricsRecorder.bindEventJournalQueue(queue);
        log.info(
                "Event journal async writer started (queueCapacity={}, batchSize={}, flushIntervalMs={}, writerThreads={})",
                properties.getQueueCapacity(),
                properties.getBatchSize(),
                properties.getFlushIntervalMs(),
                writerThreads
        );
    }

    public void enqueue(ObjectEvent event, String payloadJson) {
        recentEventCache.append(event);
        PendingEntry entry = new PendingEntry(event, payloadJson);
        if (!properties.isAsyncEnabled()) {
            persistSync(entry);
            return;
        }
        if (queue == null) {
            persistSync(entry);
            return;
        }
        if (queue.offer(entry)) {
            return;
        }
        automationMetricsRecorder.recordEventJournalSyncFallback();
        log.warn(
                "Event journal queue full (capacity={}); sync persist for {} event {}",
                properties.getQueueCapacity(),
                event.objectPath(),
                event.eventName()
        );
        persistSync(entry);
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
                List<PendingEntry> batch = new ArrayList<>(batchSize);
                PendingEntry first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
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
        List<PendingEntry> batch = new ArrayList<>(Math.max(1, properties.getBatchSize()));
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
    }

    private void flushBatch(List<PendingEntry> batch) {
        List<EventHistoryEntity> entities = batch.stream().map(PendingEntry::toEntity).toList();
        try {
            batchPersister.persistBatch(entities);
            automationMetricsRecorder.recordEventJournalFlushed(batch.size());
        } catch (Exception ex) {
            log.error("Event journal batch flush failed (size={}); falling back to per-event sync persist", batch.size(), ex);
            for (PendingEntry entry : batch) {
                try {
                    persistSync(entry);
                } catch (Exception singleEx) {
                    log.error(
                            "Event journal sync fallback failed for {} event {}",
                            entry.event().objectPath(),
                            entry.event().eventName(),
                            singleEx
                    );
                }
            }
        }
    }

    private void persistSync(PendingEntry entry) {
        batchPersister.persistOne(entry.toEntity());
        automationMetricsRecorder.recordEventJournalFlushed(1);
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
