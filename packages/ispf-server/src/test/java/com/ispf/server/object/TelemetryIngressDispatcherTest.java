package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryIngressDispatcherTest {

    @Mock
    private RuntimeTelemetryCoalescer coalescer;

    @Mock
    private AutomationMetricsRecorder metrics;

    @Mock
    private BindingDependencyIndex bindingDependencyIndex;

    private TelemetryIngressDispatcher dispatcher;

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }

    @Test
    void drainsCoalescedUpdatesToCoalescer() throws Exception {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setIngressQueueEnabled(true);
        properties.setIngressElasticWorkers(false);
        properties.setIngressWorkerThreadsMin(1);
        properties.setIngressWorkerThreadsMax(1);
        properties.setIngressDrainBatchSize(16);
        dispatcher = new TelemetryIngressDispatcher(properties, coalescer, metrics, bindingDependencyIndex);
        dispatcher.start();

        DataSchema schema = DataSchema.builder("temperature").field("raw", FieldType.DOUBLE).build();
        DataRecord first = DataRecord.single(schema, Map.of("raw", 1.0));
        DataRecord second = DataRecord.single(schema, Map.of("raw", 2.0));
        dispatcher.submit("root.dev.sensor", "temperature", first, null);
        dispatcher.submit("root.dev.sensor", "temperature", second, null);

        verify(coalescer, timeout(5000).atLeastOnce()).publishCoalescedBatch(anyList());
    }

    @Test
    void bindingConsumerTicksBypassLastValueWinsCoalesce() throws Exception {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setIngressQueueEnabled(true);
        properties.setIngressQueueCoalesceEnabled(true);
        properties.setIngressElasticWorkers(false);
        properties.setIngressWorkerThreadsMin(1);
        properties.setIngressWorkerThreadsMax(1);
        properties.setIngressDrainBatchSize(16);
        org.mockito.Mockito.when(bindingDependencyIndex.hasConsumers("root.dev.sensor", "temperature"))
                .thenReturn(true);

        CountDownLatch samples = new CountDownLatch(2);
        AtomicInteger published = new AtomicInteger();
        doAnswer(invocation -> {
            List<?> batch = invocation.getArgument(0);
            published.addAndGet(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                samples.countDown();
            }
            return null;
        }).when(coalescer).publishCoalescedBatch(anyList());

        dispatcher = new TelemetryIngressDispatcher(properties, coalescer, metrics, bindingDependencyIndex);
        dispatcher.start();

        DataSchema schema = DataSchema.builder("temperature").field("raw", FieldType.DOUBLE).build();
        DataRecord first = DataRecord.single(schema, Map.of("raw", 1.0));
        DataRecord second = DataRecord.single(schema, Map.of("raw", 2.0));
        dispatcher.submit("root.dev.sensor", "temperature", first, null);
        dispatcher.submit("root.dev.sensor", "temperature", second, null);

        assertThat(samples.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(published.get()).isEqualTo(2);
    }
}
