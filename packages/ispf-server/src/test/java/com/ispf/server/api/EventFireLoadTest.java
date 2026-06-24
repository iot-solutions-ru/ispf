package com.ispf.server.api;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Baseline load test for {@code POST /events/fire} and {@code GET /events}.
 * Uses a real HTTP port and {@link HttpClient} — {@code MockMvc} is not thread-safe.
 */
@Isolated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class EventFireLoadTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String EVENT_NAME = "thresholdExceeded";
    private static final int CONCURRENCY = 150;
    private static final long P99_CEILING_MS = resolveP99CeilingMs();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectManager objectManager;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeEach
    void awaitReady() throws Exception {
        awaitPlatformReady();
        objectManager.require(DEMO_DEVICE);
    }

    @Test
    void fireEventP99UnderCeilingAt150Concurrent() throws Exception {
        runConcurrentLoad("fire_event", CONCURRENCY, () -> {
            String query = "objectPath=" + encode(DEMO_DEVICE) + "&eventName=" + encode(EVENT_NAME);
            URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/events/fire?" + query);
            return HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(30))
                    .build();
        });
    }

    @Test
    void listEventsP99UnderCeilingAt150Concurrent() throws Exception {
        runConcurrentLoad("list_events", CONCURRENCY, () -> {
            URI uri = URI.create(
                    "http://127.0.0.1:" + port + "/api/v1/events?objectPath=" + encode(DEMO_DEVICE) + "&limit=30"
            );
            return HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
        });
    }

    private void runConcurrentLoad(String label, int concurrency, RequestSupplier supplier) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                long t0 = System.nanoTime();
                HttpResponse<Void> response = httpClient.send(supplier.get(), HttpResponse.BodyHandlers.discarding());
                assertEquals(200, response.statusCode(), label + " status");
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
                "%s load: n=%d p50=%dms p99=%dms max=%dms%n",
                label,
                latencies.size(),
                latencies.get(latencies.size() / 2),
                p99,
                stats.getMax()
        );

        assertTrue(
                p99 <= P99_CEILING_MS,
                label + " p99 " + p99 + "ms exceeds ceiling " + P99_CEILING_MS + "ms"
        );
    }

    private void awaitPlatformReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        URI infoUri = URI.create("http://127.0.0.1:" + port + "/api/v1/info");
        while (System.nanoTime() < deadline) {
            if (objectManager.tree().findByPath(DEMO_DEVICE).isPresent()) {
                try {
                    HttpResponse<Void> response = httpClient.send(
                            HttpRequest.newBuilder(infoUri).GET().timeout(Duration.ofSeconds(2)).build(),
                            HttpResponse.BodyHandlers.discarding()
                    );
                    if (response.statusCode() == 200) {
                        return;
                    }
                } catch (Exception ignored) {
                    // Tomcat may still be binding when ApplicationReady listeners are running.
                }
            }
            Thread.sleep(50);
        }
        fail("Platform HTTP endpoint not ready on port " + port);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
        return 3_000L;
    }

    @FunctionalInterface
    private interface RequestSupplier {
        HttpRequest get() throws Exception;
    }
}
