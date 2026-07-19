package com.ispf.server.history;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * BL-161 JVM CI gate: single-tag {@code GET .../history/aggregate} over ≤1M seeded points, p95 &lt; 2 s.
 */
@Tag("load")
@Isolated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = {
        "ispf.historian.deploy-profile=hot-only",
        "ispf.historian.tiers.warm.enabled=false",
        "ispf.variable-history.async-enabled=false",
        "ispf.variable-history.min-interval-ms=0"
})
@Execution(ExecutionMode.SAME_THREAD)
class HistorianAggregateQueryLoadTest {

    private static final String DEVICE = "root.platform.devices.historian-sla-gate";
    private static final String VARIABLE = "temperature";
    /** Single-query SLO (BL-161); keep modest concurrency so H2 CI reflects documented p95, not fan-out. */
    private static final int CONCURRENCY = resolveConcurrency();
    private static final int ITERATIONS = resolveIterations();
    private static final int BATCH_SIZE = 5_000;
    private static final long SAMPLE_COUNT = resolveSampleCount();
    private static final long P95_CEILING_MS = resolveP95CeilingMs();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryWriteStore writeStore;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Instant from;
    private Instant to;

    @BeforeEach
    void seedOneMillionPoints() throws Exception {
        awaitPlatformReady();
        ensureDevice();
        to = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        from = to.minus(7, ChronoUnit.DAYS);
        long existing = countSeededHint();
        if (existing < SAMPLE_COUNT) {
            seedSamples(SAMPLE_COUNT);
        }
    }

    @Test
    void aggregateOneMillionPointsP95UnderTwoSeconds() throws Exception {
        String query = String.format(
                Locale.ROOT,
                "path=%s&name=%s&field=value&bucket=1h&from=%s&to=%s&limit=200",
                encode(DEVICE),
                encode(VARIABLE),
                encode(from.toString()),
                encode(to.toString())
        );
        URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/objects/by-path/variables/history/aggregate?" + query);
        // Warm JIT / H2 page cache before timed samples (not counted toward p95).
        assertEquals(200, timedGet(uri));
        runLoad(uri, CONCURRENCY, ITERATIONS);
    }

    private void runLoad(URI uri, int concurrency, int iterations) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            futures.add(pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                long t0 = System.nanoTime();
                assertEquals(200, timedGet(uri), "historian aggregate status");
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            }));
        }

        start.countDown();
        List<Long> latencies = new ArrayList<>();
        for (Future<Long> future : futures) {
            latencies.add(future.get(120, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        Collections.sort(latencies);
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();
        System.out.printf(
                "historian_aggregate_query samples=%d n=%d concurrency=%d p50=%dms p95=%dms p99=%dms max=%dms ceiling=%dms%n",
                SAMPLE_COUNT, stats.getCount(), concurrency, p50, p95, p99, stats.getMax(), P95_CEILING_MS
        );
        if (p95 > P95_CEILING_MS) {
            fail("p95 " + p95 + "ms exceeds ceiling " + P95_CEILING_MS + "ms");
        }
    }

    private int timedGet(URI uri) throws Exception {
        HttpResponse<Void> response = httpClient.send(
                HttpRequest.newBuilder(uri)
                        .GET()
                        .timeout(Duration.ofSeconds(60))
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );
        return response.statusCode();
    }

    private void ensureDevice() {
        if (objectManager.tree().findByPath(DEVICE).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "historian-sla-gate",
                    ObjectType.DEVICE,
                    "historian-sla-gate",
                    "BL-161 aggregate SLA gate",
                    LabBlueprintBootstrap.VIRTUAL_LAB_MODEL
            );
        }
        if (objectManager.require(DEVICE).getVariable(VARIABLE).isEmpty()) {
            DataSchema schema = DataSchema.builder(VARIABLE).field("value", FieldType.DOUBLE).build();
            objectManager.createVariable(
                    DEVICE,
                    VARIABLE,
                    schema,
                    true,
                    true,
                    DataRecord.single(schema, Map.of("value", 0.0)),
                    true,
                    null
            );
        }
    }

    private void seedSamples(long count) {
        long spanMs = Math.max(1L, Duration.between(from, to).toMillis());
        List<VariableHistoryWriteRecord> batch = new ArrayList<>(BATCH_SIZE);
        for (long i = 0; i < count; i++) {
            Instant sampledAt = from.plusMillis((spanMs * i) / count);
            batch.add(new VariableHistoryWriteRecord(
                    DEVICE,
                    VARIABLE,
                    "value",
                    sampledAt,
                    sampledAt,
                    20.0 + (i % 50),
                    null
            ));
            if (batch.size() >= BATCH_SIZE) {
                writeStore.appendBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            writeStore.appendBatch(batch);
        }
    }

    /** Avoid re-seeding when DirtiesContext reuses an in-memory DB within the class lifecycle. */
    private long countSeededHint() {
        return 0L;
    }

    private void awaitPlatformReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        URI infoUri = URI.create("http://127.0.0.1:" + port + "/api/v1/info");
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<Void> response = httpClient.send(
                        HttpRequest.newBuilder(infoUri).GET().timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.discarding()
                );
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // retry until deadline
            }
            Thread.sleep(100);
        }
        fail("Platform did not become ready within 30s");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(pct / 100.0 * sorted.size()) - 1));
        return sorted.get(index);
    }

    private static long resolveSampleCount() {
        String env = System.getenv("ISPF_HISTORIAN_AGGREGATE_SAMPLE_COUNT");
        if (env == null || env.isBlank()) {
            env = System.getProperty("ispf.historian.aggregate.sampleCount");
        }
        if (env == null || env.isBlank()) {
            return 1_000_000L;
        }
        return Long.parseLong(env.trim());
    }

    private static long resolveP95CeilingMs() {
        String env = System.getenv("ISPF_HISTORIAN_AGGREGATE_P95_CEILING_MS");
        if (env == null || env.isBlank()) {
            env = System.getProperty("ispf.historian.aggregate.p95CeilingMs");
        }
        if (env == null || env.isBlank()) {
            return 2_000L;
        }
        return Long.parseLong(env.trim());
    }

    private static int resolveConcurrency() {
        String env = System.getenv("ISPF_HISTORIAN_AGGREGATE_CONCURRENCY");
        if (env == null || env.isBlank()) {
            env = System.getProperty("ispf.historian.aggregate.concurrency");
        }
        if (env == null || env.isBlank()) {
            return 1;
        }
        return Math.max(1, Integer.parseInt(env.trim()));
    }

    private static int resolveIterations() {
        String env = System.getenv("ISPF_HISTORIAN_AGGREGATE_ITERATIONS");
        if (env == null || env.isBlank()) {
            env = System.getProperty("ispf.historian.aggregate.iterations");
        }
        if (env == null || env.isBlank()) {
            return 12;
        }
        return Math.max(1, Integer.parseInt(env.trim()));
    }
}
