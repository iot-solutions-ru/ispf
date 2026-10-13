package com.ispf.server.history;

import com.ispf.server.config.VariableHistoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.clickhouse.ClickHouseContainer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ADR-0074: ClickHouse historian quality round-trip (GOOD / UNCERTAIN / BAD, null = unknown).
 *
 * <p>Opt-in. Default CI does not start Docker. A green default {@code test} task does not prove
 * this class. Run:
 * {@code ISPF_CLICKHOUSE_QUALITY_TCK=true ./gradlew :packages:ispf-server:test --tests com.ispf.server.history.HistorianQualityClickHouseTckTest}
 *
 * <p>Image tag matches {@code deploy/docker-compose.clickhouse.yml}
 * ({@code clickhouse/clickhouse-server:24.8}). Cassandra/Scylla stays quality-incapable.
 */
@EnabledIfEnvironmentVariable(named = "ISPF_CLICKHOUSE_QUALITY_TCK", matches = "true")
class HistorianQualityClickHouseTckTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void clickHouseRoundTripsGoodUncertainBadAndLeavesUnknownNull() throws Exception {
        try (ClickHouseContainer clickhouse = new ClickHouseContainer("clickhouse/clickhouse-server:24.8")
                .withStartupTimeout(Duration.ofMinutes(3))) {
            clickhouse.start();

            VariableHistoryProperties properties = new VariableHistoryProperties();
            properties.setStore("clickhouse");
            properties.getClickhouse().setUrl(clickhouse.getHttpUrl());
            properties.getClickhouse().setUsername(clickhouse.getUsername());
            properties.getClickhouse().setPassword(clickhouse.getPassword());
            properties.getClickhouse().setDatabase("ispf");
            properties.getClickhouse().setTable("variable_samples");

            ClickHouseVariableHistoryStore store = new ClickHouseVariableHistoryStore(properties);
            store.ensureSchema();

            Instant t0 = Instant.parse("2026-10-13T08:00:00Z");
            store.appendBatch(List.of(
                    sample(t0, "GOOD"),
                    sample(t0.plusSeconds(1), "uncertain"),
                    sample(t0.plusSeconds(2), "bad")
            ));
            store.appendOne(sample(t0.plusSeconds(3), null));
            store.appendOne(sample(t0.plusSeconds(4), " "));

            List<String> stored = selectStoredQualities(clickhouse);
            assertEquals(5, stored.size());
            assertEquals(List.of("GOOD", "UNCERTAIN", "BAD"), stored.subList(0, 3));
            assertNull(stored.get(3));
            assertNull(stored.get(4));

            List<VariableHistoryService.VariableHistorySample> samples = awaitSamples(store, 5);
            assertEquals(5, samples.size());
            assertEquals(stored, samples.stream().map(VariableHistoryService.VariableHistorySample::quality).toList());
            assertEquals(21.5, samples.getFirst().value());
        }
    }

    /**
     * Column values as stored. {@code query()} also normalizes, so this is the write-side proof
     * (same role as the jdbc TCK reading {@code variable_samples.quality}).
     */
    private static List<String> selectStoredQualities(ClickHouseContainer clickhouse) throws Exception {
        String sql = "SELECT quality FROM ispf.variable_samples ORDER BY sampled_at FORMAT TabSeparated";
        String url = clickhouse.getHttpUrl()
                + "/?database=ispf"
                + "&user=" + URLEncoder.encode(clickhouse.getUsername(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(clickhouse.getPassword(), StandardCharsets.UTF_8)
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertEquals(200, response.statusCode(), response.body());
            return response.body().lines()
                    .map(line -> "\\N".equals(line) ? null : line)
                    .toList();
        }
    }

    private static List<VariableHistoryService.VariableHistorySample> awaitSamples(
            ClickHouseVariableHistoryStore store,
            int expected
    ) throws InterruptedException {
        List<VariableHistoryService.VariableHistorySample> samples = List.of();
        Instant deadline = Instant.now().plusSeconds(10);
        while (!Instant.now().isAfter(deadline)) {
            samples = store.query(
                    "root.platform.devices.demo",
                    "temperature",
                    "value",
                    null,
                    null,
                    10
            );
            if (samples.size() >= expected) {
                return samples;
            }
            Thread.sleep(200);
        }
        return samples;
    }

    private static VariableHistoryWriteRecord sample(Instant at, String quality) {
        return VariableHistoryWriteRecord.ingested(
                "root.platform.devices.demo",
                "temperature",
                "value",
                at,
                21.5,
                null,
                quality
        );
    }
}
