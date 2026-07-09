package com.ispf.server.platform.analytics;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.history.VariableHistoryService;
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

import java.net.URI;
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
 * BL-210 JVM gate: {@code POST /api/v1/platform/analytics/query} at 10 tags × 7d × 1h buckets.
 */
@Tag("load")
@Isolated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class AnalyticsMultiTagQueryLoadTest {

    private static final String FOLDER = "root.platform.devices.analytics-load-gate";
    private static final int TAG_COUNT = 10;
    private static final int CONCURRENCY = 50;
    private static final long P95_CEILING_MS = resolveP95CeilingMs();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String queryBody;

    @BeforeEach
    void seedTags() throws Exception {
        awaitPlatformReady();
        ensureFolder();
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < TAG_COUNT; i++) {
            String name = "tag-" + i;
            String path = FOLDER + "." + name;
            paths.add(ensureDevice(path, name));
            seedHistory(path, "temperature");
        }
        Instant to = Instant.now();
        Instant from = to.minus(7, ChronoUnit.DAYS);
        queryBody = buildQueryJson(paths, from, to);
    }

    @Test
    void multiTagQueryP95UnderCeilingAtFiftyConcurrent() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/platform/analytics/query");
        runConcurrentLoad(uri, queryBody, CONCURRENCY);
    }

    private void runConcurrentLoad(URI uri, String body, int concurrency) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            futures.add(pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                long t0 = System.nanoTime();
                HttpResponse<Void> response = httpClient.send(
                        HttpRequest.newBuilder(uri)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .timeout(Duration.ofSeconds(30))
                                .build(),
                        HttpResponse.BodyHandlers.discarding()
                );
                assertEquals(200, response.statusCode(), "analytics query status");
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
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();
        System.out.printf(
                "analytics_multi_tag_query n=%d p50=%dms p95=%dms p99=%dms max=%dms ceiling=%dms%n",
                stats.getCount(), p50, p95, p99, stats.getMax(), P95_CEILING_MS
        );
        if (p95 > P95_CEILING_MS) {
            fail("p95 " + p95 + "ms exceeds ceiling " + P95_CEILING_MS + "ms");
        }
    }

    private void ensureFolder() {
        if (objectManager.tree().findByPath(FOLDER).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "analytics-load-gate",
                    ObjectType.CUSTOM,
                    "analytics-load-gate",
                    "BL-210 load gate",
                    null
            );
        }
    }

    private String ensureDevice(String path, String name) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    FOLDER,
                    name,
                    ObjectType.DEVICE,
                    name,
                    "BL-210 load gate",
                    LabBlueprintBootstrap.VIRTUAL_LAB_MODEL
            );
        }
        if (objectManager.require(path).getVariable("temperature").isEmpty()) {
            DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
            objectManager.createVariable(
                    path,
                    "temperature",
                    schema,
                    true,
                    true,
                    DataRecord.single(schema, Map.of("value", 0.0)),
                    true,
                    null
            );
        }
        return path;
    }

    private void seedHistory(String path, String variable) {
        DataSchema schema = DataSchema.builder(variable).field("value", FieldType.DOUBLE).build();
        for (int i = 0; i < 5; i++) {
            objectManager.setVariableValue(path, variable, DataRecord.single(schema, Map.of("value", 20.0 + i)));
            variableHistoryService.recordVariableUpdate(path, variable);
        }
    }

    private static String buildQueryJson(List<String> paths, Instant from, Instant to) {
        StringBuilder tags = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                tags.append(',');
            }
            String path = paths.get(i);
            tags.append(String.format(
                    "{\"path\":\"%s\",\"variable\":\"temperature\",\"field\":\"value\",\"label\":\"t%d\"}",
                    path,
                    i
            ));
        }
        return String.format(
                Locale.ROOT,
                "{\"tags\":[%s],\"from\":\"%s\",\"to\":\"%s\",\"bucket\":\"1h\",\"agg\":\"avg\",\"maxBuckets\":168}",
                tags,
                from.toString(),
                to.toString()
        );
    }

    private static long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(pct / 100.0 * sorted.size()) - 1));
        return sorted.get(index);
    }

    private static long resolveP95CeilingMs() {
        String env = System.getenv("ISPF_ANALYTICS_LOAD_P95_CEILING_MS");
        if (env == null || env.isBlank()) {
            env = System.getProperty("ispf.analytics.load.p95CeilingMs");
        }
        if (env == null || env.isBlank()) {
            return 3_000L;
        }
        return Long.parseLong(env.trim());
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
}
