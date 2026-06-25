package com.ispf.server.event;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.persistence.entity.EventHistoryEntity;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventJournalAsyncWriterTest {

    private static final String OBJECT_PATH = "root.platform.devices.demo-sensor-01";

    @Mock
    private EventJournalBatchPersister batchPersister;

    @Mock
    private AutomationMetricsRecorder automationMetricsRecorder;

    private EventJournalProperties properties;
    private RecentEventCache recentEventCache;
    private EventJournalAsyncWriter writer;

    @BeforeEach
    void setUp() {
        reset(batchPersister, automationMetricsRecorder);
        properties = new EventJournalProperties();
        properties.setAsyncEnabled(true);
        properties.setQueueCapacity(4);
        properties.setBatchSize(2);
        properties.setFlushIntervalMs(60_000);
        properties.setWriterThreads(1);
        properties.setRecentCacheSize(10);
        recentEventCache = new RecentEventCache(properties);
        writer = new EventJournalAsyncWriter(properties, batchPersister, recentEventCache, automationMetricsRecorder);
        writer.start();
    }

    @AfterEach
    void tearDown() {
        if (writer != null) {
            writer.shutdown();
        }
    }

    @Test
    void enqueueReturnsEventIdBeforePersistence() {
        ObjectEvent event = sampleEvent("evt-1");
        writer.enqueue(event, "{}");

        verify(batchPersister, never()).persistOne(any());
        verify(batchPersister, never()).persistBatch(anyList());
        assertTrue(recentEventCache.findLatest(OBJECT_PATH, "thresholdExceeded").isPresent());
    }

    @Test
    void batchFlushesToDatabase() throws Exception {
        properties.setFlushIntervalMs(50);
        writer.shutdown();
        writer = new EventJournalAsyncWriter(properties, batchPersister, recentEventCache, automationMetricsRecorder);
        writer.start();

        writer.enqueue(sampleEvent("evt-1"), "{}");
        writer.enqueue(sampleEvent("evt-2"), "{}");

        writer.awaitQueueDrain(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        ArgumentCaptor<List<EventHistoryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersister, atLeastOnce()).persistBatch(captor.capture());
        int totalPersisted = captor.getAllValues().stream().mapToInt(List::size).sum();
        assertEquals(2, totalPersisted);
    }

    @Test
    void queueFullFallsBackToSyncPersist() throws Exception {
        writer.shutdown();
        reset(batchPersister, automationMetricsRecorder);
        properties.setQueueCapacity(1);
        properties.setFlushIntervalMs(60_000);
        properties.setBatchSize(100);
        properties.setWriterThreads(1);
        CountDownLatch releaseBatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseBatch.await(2, TimeUnit.SECONDS);
            return null;
        }).when(batchPersister).persistBatch(anyList());

        writer = new EventJournalAsyncWriter(properties, batchPersister, recentEventCache, automationMetricsRecorder);
        writer.start();

        writer.enqueue(sampleEvent("evt-1"), "{}");
        Thread.sleep(20);
        writer.enqueue(sampleEvent("evt-2"), "{}");
        writer.enqueue(sampleEvent("evt-3"), "{}");

        verify(batchPersister, times(1)).persistOne(any(EventHistoryEntity.class));
        verify(automationMetricsRecorder, times(1)).recordEventJournalSyncFallback();
        releaseBatch.countDown();
    }

    @Test
    void shutdownDrainsRemainingEvents() throws Exception {
        writer.enqueue(sampleEvent("evt-1"), "{}");
        writer.enqueue(sampleEvent("evt-2"), "{}");
        writer.shutdown();
        writer = null;

        verify(batchPersister, atLeastOnce()).persistBatch(anyList());
    }

    @Test
    void syncModePersistsImmediately() {
        writer.shutdown();
        reset(batchPersister, automationMetricsRecorder);
        properties.setAsyncEnabled(false);
        writer = new EventJournalAsyncWriter(properties, batchPersister, recentEventCache, automationMetricsRecorder);
        writer.start();

        ObjectEvent event = sampleEvent("sync-1");
        writer.enqueue(event, "{}");

        verify(batchPersister, times(1)).persistOne(any(EventHistoryEntity.class));
        verify(batchPersister, never()).persistBatch(anyList());
        writer.shutdown();
        writer = null;
    }

    @Test
    void recentCacheListsNewestFirstPerObject() {
        writer.enqueue(sampleEvent("evt-1"), "{}");
        writer.enqueue(sampleEvent("evt-2"), "{}");
        writer.enqueue(new ObjectEvent(
                "evt-other",
                "root.other",
                "otherEvent",
                EventLevel.INFO,
                DataRecord.empty(DataSchema.builder("payload").build()),
                Instant.now()
        ), "{}");

        List<ObjectEvent> events = recentEventCache.query(OBJECT_PATH, 10);
        assertEquals(2, events.size());
        assertEquals("evt-2", events.get(0).id());
        assertEquals("evt-1", events.get(1).id());
    }

    @Test
    void recentCacheDisabledWhenSizeZero() {
        writer.shutdown();
        reset(batchPersister, automationMetricsRecorder);
        properties.setRecentCacheSize(0);
        recentEventCache = new RecentEventCache(properties);
        writer = new EventJournalAsyncWriter(properties, batchPersister, recentEventCache, automationMetricsRecorder);
        writer.start();

        writer.enqueue(sampleEvent("evt-1"), "{}");
        assertFalse(recentEventCache.isEnabled());
        assertTrue(recentEventCache.query(OBJECT_PATH, 10).isEmpty());
    }

    private static ObjectEvent sampleEvent(String id) {
        DataSchema schema = DataSchema.builder("thresholdPayload")
                .field("value", FieldType.DOUBLE)
                .build();
        return new ObjectEvent(
                id,
                OBJECT_PATH,
                "thresholdExceeded",
                EventLevel.WARNING,
                DataRecord.single(schema, java.util.Map.of("value", 42.0)),
                Instant.parse("2026-06-25T12:00:00Z")
        );
    }
}
