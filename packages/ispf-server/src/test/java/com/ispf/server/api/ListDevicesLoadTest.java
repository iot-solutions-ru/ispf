package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baseline for GAP_REGISTRY scale item: p99 {@code list_devices} at 150 concurrent readers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListDevicesLoadTest {

    private static final String PARENT = "root.platform.devices";
    private static final int DEVICE_COUNT = 120;
    private static final int CONCURRENCY = 150;
    /**
     * CI-friendly ceiling (ms). Override for production-sized DB staging:
     * {@code ISPF_LOAD_P99_CEILING_MS=3000} or {@code -Dispf.load.p99CeilingMs=3000}.
     */
    private static final long P99_CEILING_MS = resolveP99CeilingMs();

    @Autowired
    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void seedDevices() throws Exception {
        for (int i = 0; i < DEVICE_COUNT; i++) {
            String name = "load-perf-" + i;
            String path = PARENT + "." + name;
            mockMvc.perform(delete("/api/v1/objects/by-path").param("path", path));
            mockMvc.perform(post("/api/v1/objects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "parentPath": "%s",
                                      "name": "%s",
                                      "type": "DEVICE",
                                      "displayName": "Load perf %d"
                                    }
                                    """.formatted(PARENT, name, i)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void listDevicesP99UnderCeilingAt150Concurrent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENCY; i++) {
            futures.add(pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                long t0 = System.nanoTime();
                mockMvc.perform(get("/api/v1/objects")
                                .param("parent", PARENT)
                                .param("lite", "true"))
                        .andExpect(status().isOk());
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            }));
        }

        start.countDown();
        List<Long> latencies = new ArrayList<>();
        for (Future<Long> future : futures) {
            latencies.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        Collections.sort(latencies);
        long p99 = latencies.get((int) Math.ceil(latencies.size() * 0.99) - 1);
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();

        System.out.printf(
                "list_devices load: n=%d p50=%dms p99=%dms max=%dms%n",
                latencies.size(),
                latencies.get(latencies.size() / 2),
                p99,
                stats.getMax()
        );

        assertTrue(
                p99 <= P99_CEILING_MS,
                "p99 " + p99 + "ms exceeds ceiling " + P99_CEILING_MS + "ms"
        );
    }

    private static long resolveP99CeilingMs() {
        String property = System.getProperty("ispf.load.p99CeilingMs");
        if (property != null && !property.isBlank()) {
            return Long.parseLong(property.trim());
        }
        String env = System.getenv("ISPF_LOAD_P99_CEILING_MS");
        if (env != null && !env.isBlank()) {
            return Long.parseLong(env.trim());
        }
        return 2_500L;
    }
}
