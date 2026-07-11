package com.ispf.server.history;

import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.persistence.entity.VariableSampleEntity;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VariableHistoryAsyncWriterTest {

    @Mock
    private VariableHistoryBatchPersister batchPersister;

    @Mock
    private AutomationMetricsRecorder automationMetricsRecorder;

    private VariableHistoryProperties properties;
    private VariableHistoryAsyncWriter writer;

    @BeforeEach
    void setUp() {
        reset(batchPersister, automationMetricsRecorder);
        properties = new VariableHistoryProperties();
        properties.setAsyncEnabled(true);
        properties.setQueueCapacity(4);
        properties.setBatchSize(2);
        properties.setFlushIntervalMs(60_000);
        properties.setWriterThreads(1);
        properties.setElasticWriterEnabled(false);
        properties.setOverflowCoalesceEnabled(false);
        writer = new VariableHistoryAsyncWriter(properties, batchPersister, automationMetricsRecorder);
        writer.start();
    }

    @AfterEach
    void tearDown() {
        if (writer != null) {
            writer.shutdown();
        }
    }

    @Test
    void enqueueReturnsBeforePersistence() throws InterruptedException {
        writer.shutdown();
        properties.setFlushIntervalMs(3_600_000);
        writer = new VariableHistoryAsyncWriter(properties, batchPersister, automationMetricsRecorder);
        CountDownLatch persistEntered = new CountDownLatch(1);
        CountDownLatch allowPersist = new CountDownLatch(1);
        doAnswer(invocation -> {
            persistEntered.countDown();
            if (!allowPersist.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("allowPersist timeout");
            }
            return null;
        }).when(batchPersister).persistBatch(anyList());
        writer.start();

        assertTimeoutPreemptively(java.time.Duration.ofMillis(250), () ->
                writer.enqueue(List.of(
                        sample("root.a", "temperature", "value", 1.0),
                        sample("root.a", "temperature", "value", 2.0)
                ))
        );

        assertTrue(persistEntered.await(5, TimeUnit.SECONDS), "worker should enter persistBatch while enqueue returns");
        allowPersist.countDown();
        writer.awaitQueueDrain(5, TimeUnit.SECONDS);
        verify(batchPersister, times(1)).persistBatch(anyList());
    }

    @Test
    void flushesBatchWhenBatchSizeReached() throws Exception {
        writer.enqueue(List.of(
                sample("root.a", "temperature", "value", 1.0),
                sample("root.a", "temperature", "value", 2.0)
        ));

        writer.awaitQueueDrain(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        ArgumentCaptor<List<VariableSampleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersister, timeout(5000).atLeastOnce()).persistBatch(captor.capture());
        int totalPersisted = captor.getAllValues().stream().mapToInt(List::size).sum();
        assertTrue(totalPersisted >= 2, "expected at least 2 samples persisted, got " + totalPersisted);
        verify(automationMetricsRecorder, atLeastOnce()).recordVariableHistoryFlushed(2);
    }

    @Test
    void overflowCoalesceWhenQueueFull() throws Exception {
        writer.shutdown();
        properties.setQueueCapacity(1);
        properties.setBatchSize(1);
        properties.setFlushIntervalMs(3_600_000);
        properties.setElasticWriterEnabled(false);
        properties.setOverflowCoalesceEnabled(true);
        writer = new VariableHistoryAsyncWriter(properties, batchPersister, automationMetricsRecorder);
        CountDownLatch blockFlush = new CountDownLatch(1);
        doAnswer(invocation -> {
            blockFlush.await(5, TimeUnit.SECONDS);
            return null;
        }).when(batchPersister).persistBatch(anyList());
        writer.start();

        writer.enqueue(List.of(sample("root.a", "temperature", "value", 1.0)));
        Thread.sleep(30);
        writer.enqueue(List.of(
                sample("root.b", "temperature", "value", 2.0),
                sample("root.c", "temperature", "value", 3.0)
        ));

        verify(batchPersister, never()).persistOne(org.mockito.ArgumentMatchers.any());
        verify(automationMetricsRecorder, atLeastOnce()).recordVariableHistoryOverflowCoalesced();
        blockFlush.countDown();
        writer.awaitQueueDrain(5, TimeUnit.SECONDS);
    }

    @Test
    void syncFallbackWhenQueueFullAndOverflowDisabled() throws Exception {
        writer.shutdown();
        properties.setQueueCapacity(1);
        properties.setBatchSize(1);
        properties.setFlushIntervalMs(3600_000);
        properties.setOverflowCoalesceEnabled(false);
        properties.setElasticWriterEnabled(false);
        writer = new VariableHistoryAsyncWriter(properties, batchPersister, automationMetricsRecorder);
        CountDownLatch persistEntered = new CountDownLatch(1);
        CountDownLatch allowPersist = new CountDownLatch(1);
        doAnswer(invocation -> {
            persistEntered.countDown();
            allowPersist.await(5, TimeUnit.SECONDS);
            return null;
        }).when(batchPersister).persistBatch(anyList());
        writer.start();

        writer.enqueue(List.of(sample("root.a", "temperature", "value", 1.0)));
        assertTrue(persistEntered.await(5, TimeUnit.SECONDS), "worker should block on first batch flush");

        writer.enqueue(List.of(
                sample("root.b", "temperature", "value", 2.0),
                sample("root.c", "temperature", "value", 3.0)
        ));

        verify(batchPersister, timeout(5000).atLeastOnce()).persistOne(org.mockito.ArgumentMatchers.any());
        verify(automationMetricsRecorder, timeout(5000).atLeastOnce()).recordVariableHistorySyncFallback();
        allowPersist.countDown();
    }

    @Test
    void syncPersistWhenAsyncDisabled() {
        writer.shutdown();
        properties.setAsyncEnabled(false);
        writer = new VariableHistoryAsyncWriter(properties, batchPersister, automationMetricsRecorder);
        writer.start();

        writer.enqueue(List.of(sample("root.a", "temperature", "value", 4.0)));

        verify(batchPersister, times(1)).persistBatch(anyList());
        verify(automationMetricsRecorder, times(1)).recordVariableHistoryFlushed(1);
    }

    private static VariableSampleEntity sample(String path, String variable, String field, double value) {
        VariableSampleEntity entity = new VariableSampleEntity();
        entity.setObjectPath(path);
        entity.setVariableName(variable);
        entity.setFieldName(field);
        entity.setSampledAt(Instant.now());
        entity.setValueDouble(value);
        return entity;
    }
}
