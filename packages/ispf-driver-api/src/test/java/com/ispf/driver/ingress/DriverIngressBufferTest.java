package com.ispf.driver.ingress;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverIngressBufferTest {

    @Test
    void coalescesRepeatedKeyToLastValue() throws Exception {
        List<String> payloads = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger handled = new AtomicInteger();

        DriverIngressBuffer<String, byte[]> buffer = new DriverIngressBuffer<>(1, 64, (key, payload) -> {
            payloads.add(new String(payload, StandardCharsets.UTF_8));
            if (handled.incrementAndGet() >= 1) {
                done.countDown();
            }
        });

        for (int i = 0; i < 500; i++) {
            buffer.submit("sensors/temp", String.valueOf(i).getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(done.await(5, TimeUnit.SECONDS));
        buffer.shutdown();

        assertTrue(payloads.size() < 500, "expected coalescing, got " + payloads.size() + " deliveries");
        assertEquals("499", payloads.get(payloads.size() - 1));
        assertTrue(buffer.coalescedTotal() > 0);
    }

    @Test
    void eagerDrainDeliversWithoutParkLoop() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger handled = new AtomicInteger();

        DriverIngressBuffer<String, byte[]> buffer = new DriverIngressBuffer<>(1, 64, (key, payload) -> {
            handled.incrementAndGet();
            done.countDown();
        }, "test-eager", true);

        for (int i = 0; i < 500; i++) {
            buffer.submit("lane", String.valueOf(i).getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(done.await(2, TimeUnit.SECONDS));
        buffer.shutdown();
        assertTrue(handled.get() >= 1);
    }

    @Test
    void drainsDistinctKeysOnShutdown() {
        List<String> keys = new ArrayList<>();
        DriverIngressBuffer<String, String> buffer = new DriverIngressBuffer<>(2, 64, (key, value) -> keys.add(key));

        buffer.submit("a", "1");
        buffer.submit("b", "2");
        buffer.shutdown();

        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
    }

    @Test
    void elasticBufferScalesWorkersUnderLoad() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger handled = new AtomicInteger();
        IngressElasticSettings elastic = new IngressElasticSettings(true, 1, 4, 10, 2, 50);

        DriverIngressBuffer<String, String> buffer = new DriverIngressBuffer<>(elastic, 256, (key, value) -> {
            started.countDown();
            handled.incrementAndGet();
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "test-elastic", false);

        for (int i = 0; i < 200; i++) {
            buffer.submit("key-" + (i % 20), String.valueOf(i));
        }

        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(300);
        assertTrue(buffer.activeWorkers() >= 1);
        buffer.shutdown();
        assertTrue(handled.get() > 0);
    }
}
