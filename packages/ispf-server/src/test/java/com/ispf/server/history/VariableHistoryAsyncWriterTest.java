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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
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
    void enqueueReturnsBeforePersistence() {
        writer.enqueue(List.of(sample("root.a", "temperature", "value", 1.0)));

        verify(batchPersister, never()).persistOne(org.mockito.ArgumentMatchers.any());
        verify(batchPersister, never()).persistBatch(anyList());
    }

    @Test
    void flushesBatchWhenBatchSizeReached() throws Exception {
        writer.enqueue(List.of(
                sample("root.a", "temperature", "value", 1.0),
                sample("root.a", "temperature", "value", 2.0)
        ));

        writer.awaitQueueDrain(5, TimeUnit.SECONDS);

        ArgumentCaptor<List<VariableSampleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersister, atLeastOnce()).persistBatch(captor.capture());
        assertTrue(captor.getValue().size() >= 2);
        verify(automationMetricsRecorder, atLeastOnce()).recordVariableHistoryFlushed(2);
    }

    @Test
    void syncFallbackWhenQueueFull() throws Exception {
        writer.shutdown();
        properties.setQueueCapacity(1);
        properties.setBatchSize(1);
        properties.setFlushIntervalMs(3600_000);
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

        verify(batchPersister, atLeastOnce()).persistOne(org.mockito.ArgumentMatchers.any());
        verify(automationMetricsRecorder, atLeastOnce()).recordVariableHistorySyncFallback();
        blockFlush.countDown();
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
