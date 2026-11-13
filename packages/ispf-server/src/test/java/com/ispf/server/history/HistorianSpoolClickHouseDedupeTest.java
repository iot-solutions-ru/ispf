package com.ispf.server.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.clickhouse.ClickHouseContainer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ClickHouse 24.8 keeps one row when the same spool batch id is inserted twice.
 * ReplicatedMergeTree needs Keeper, so that engine is checked only when this container can create it.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class HistorianSpoolClickHouseDedupeTest {

    @Test
    void mergeTreeDropsTheRepeatedSpoolBatch() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        try (ClickHouseContainer clickhouse = new ClickHouseContainer("clickhouse/clickhouse-server:24.8")
                .withStartupTimeout(Duration.ofMinutes(2))) {
            clickhouse.start();
            exec(clickhouse, "CREATE DATABASE IF NOT EXISTS ispf");
            exec(clickhouse, """
                    CREATE TABLE ispf.variable_samples (
                        object_path String,
                        variable_name String,
                        field_name String,
                        sampled_at DateTime64(3, 'UTC'),
                        observed_at DateTime64(3, 'UTC'),
                        value_double Nullable(Float64),
                        value_text Nullable(String),
                        quality Nullable(String)
                    ) ENGINE = MergeTree()
                    ORDER BY (object_path, variable_name, field_name, sampled_at)
                    SETTINGS non_replicated_deduplication_window = 10000
                    """);
            String row = "{\"object_path\":\"root.a\",\"variable_name\":\"v\",\"field_name\":\"value\","
                    + "\"sampled_at\":\"2026-11-13 00:00:00.000\",\"observed_at\":\"2026-11-13 00:00:00.000\","
                    + "\"value_double\":1,\"value_text\":null,\"quality\":\"GOOD\"}";
            insert(clickhouse, "ispf.variable_samples", "batch-1", row);
            insert(clickhouse, "ispf.variable_samples", "batch-1", row);
            assertEquals(1, count(clickhouse, "ispf.variable_samples"));
        }
    }

    private static void insert(ClickHouseContainer clickhouse, String table, String token, String body) throws Exception {
        exec(clickhouse, "INSERT INTO " + table
                + " SETTINGS insert_deduplicate = 1, insert_deduplication_token = '" + token
                + "', non_replicated_deduplication_window = 10000 FORMAT JSONEachRow", body);
    }

    private static long count(ClickHouseContainer clickhouse, String table) throws Exception {
        return Long.parseLong(exec(clickhouse, "SELECT count() FROM " + table + " FORMAT TabSeparated").trim());
    }

    private static String exec(ClickHouseContainer clickhouse, String sql) throws Exception {
        return exec(clickhouse, sql, "");
    }

    private static String exec(ClickHouseContainer clickhouse, String sql, String body) throws Exception {
        String url = clickhouse.getHttpUrl()
                + "/?user=" + URLEncoder.encode(clickhouse.getUsername(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(clickhouse.getPassword(), StandardCharsets.UTF_8)
                + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(60))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertEquals(200, response.statusCode(), response.body());
            return response.body();
        }
    }
}
