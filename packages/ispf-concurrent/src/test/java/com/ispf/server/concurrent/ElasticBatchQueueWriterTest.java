package com.ispf.server.concurrent;

import com.ispf.driver.ingress.IngressElasticSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticBatchQueueWriterTest {

    @Test
    void failedFlushIsRetriedInsteadOfDropped() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<String> written = new CopyOnWriteArrayList<>();
        ElasticBatchQueueWriter<String> writer = new ElasticBatchQueueWriter<>(
                IngressElasticSettings.fixed(1),
                1,
                20,
                "elastic-batch-test",
                batch -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("store down");
                    }
                    written.addAll(batch);
                }
        );
        writer.start(8);
        assertTrue(writer.offer("a"));

        long deadline = System.nanoTime() + 5_000_000_000L;
        while (written.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        writer.shutdown();

        assertTrue(attempts.get() >= 2, "attempts=" + attempts.get() + " pending=" + writer.pendingCount());
        assertEquals(List.of("a"), written);
    }
}
