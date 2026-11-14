package com.ispf.server.history;

import com.ispf.server.config.VariableHistoryProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickHouseVariableHistoryStoreTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private final CopyOnWriteArrayList<String> queries = new CopyOnWriteArrayList<>();
    private ClickHouseVariableHistoryStore store;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "Ok.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        VariableHistoryProperties properties = new VariableHistoryProperties();
        properties.setStore("clickhouse");
        properties.getClickhouse().setUrl("http://localhost:" + port);
        store = new ClickHouseVariableHistoryStore(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void appendBatchPostsJsonEachRowPayload() {
        VariableHistoryWriteRecord record = VariableHistoryWriteRecord.ingested(
                "root.platform.devices.sensor",
                "temperature",
                "value",
                Instant.parse("2025-06-25T12:00:00.123Z"),
                21.5,
                null
        );

        store.appendBatch(List.of(record));

        assertTrue(queries.stream().anyMatch(q -> q.contains("INSERT+INTO+ispf.variable_samples")));
        assertTrue(lastBody.get().contains("\"object_path\":\"root.platform.devices.sensor\""));
        assertTrue(lastBody.get().contains("\"value_double\":21.5"));
        assertTrue(lastBody.get().contains("\"quality\":null"));
    }

    @Test
    void appendBatchPostsQuality() {
        store.appendOne(VariableHistoryWriteRecord.ingested(
                "root.platform.devices.sensor",
                "temperature",
                "value",
                Instant.parse("2025-06-25T12:00:00.123Z"),
                21.5,
                null,
                "BAD"
        ));

        assertTrue(lastBody.get().contains("\"quality\":\"BAD\""));
    }

    @Test
    void ensureSchemaCreatesDatabaseAndTable() {
        store.ensureSchema();

        assertTrue(queries.stream().anyMatch(q -> q.contains("CREATE+DATABASE")));
        assertTrue(queries.stream().anyMatch(q -> q.contains("CREATE+TABLE")));
        assertTrue(queries.stream().anyMatch(q -> q.contains("quality")));
        assertTrue(
                queries.stream().anyMatch(q ->
                        q.contains("CREATE+DATABASE") && q.contains("database=default")),
                "CREATE DATABASE must use database=default so empty ClickHouse can bootstrap"
        );
    }

    @Test
    void supportsApplicationRetentionPurgeIsFalse() {
        assertEquals(false, store.supportsApplicationRetentionPurge());
    }

    @Test
    void spoolKeepsUnackedBatchUntilReplaySucceeds(@TempDir Path spoolDir) throws Exception {
        AtomicInteger insertStatus = new AtomicInteger(200);
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        queries.clear();
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            queries.add(query);
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean isInsert = query != null && query.contains("INSERT+INTO");
            int status = isInsert ? insertStatus.get() : 200;
            byte[] response = (status == 200 ? "Ok." : "fail").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        VariableHistoryProperties properties = new VariableHistoryProperties();
        properties.setStore("clickhouse");
        properties.getClickhouse().setUrl("http://localhost:" + port);
        properties.getSpool().setEnabled(true);
        properties.getSpool().setDirectory(spoolDir.toString());
        properties.getSpool().setFsync(false);
        ClickHouseVariableHistoryStore spoolStore = new ClickHouseVariableHistoryStore(properties);
        spoolStore.ensureSchema();

        insertStatus.set(500);
        VariableHistoryWriteRecord record = VariableHistoryWriteRecord.ingested(
                "root.platform.devices.sensor",
                "temperature",
                "value",
                Instant.parse("2025-06-25T12:00:00.123Z"),
                21.5,
                null
        );
        spoolStore.appendBatch(List.of(record));
        assertTrue(spoolStore.spoolStats().unackedRecords() > 0);

        insertStatus.set(200);
        spoolStore.replaySpool();
        assertEquals(0, spoolStore.spoolStats().unackedRecords());
        assertTrue(queries.stream().anyMatch(q -> q != null && q.contains("insert_deduplication_token")));
        spoolStore.closeSpool();
    }

    @Test
    void distinctSpoolDirectoriesGetDistinctDeduplicationTokens(@TempDir Path firstDir, @TempDir Path secondDir) {
        VariableHistoryProperties firstProps = new VariableHistoryProperties();
        firstProps.setStore("clickhouse");
        firstProps.getClickhouse().setUrl("http://localhost:" + port);
        firstProps.getSpool().setEnabled(true);
        firstProps.getSpool().setDirectory(firstDir.toString());
        firstProps.getSpool().setFsync(false);
        ClickHouseVariableHistoryStore first = new ClickHouseVariableHistoryStore(firstProps);
        first.ensureSchema();

        VariableHistoryProperties secondProps = new VariableHistoryProperties();
        secondProps.setStore("clickhouse");
        secondProps.getClickhouse().setUrl("http://localhost:" + port);
        secondProps.getSpool().setEnabled(true);
        secondProps.getSpool().setDirectory(secondDir.toString());
        secondProps.getSpool().setFsync(false);
        ClickHouseVariableHistoryStore second = new ClickHouseVariableHistoryStore(secondProps);
        second.ensureSchema();

        assertNotEquals(first.spoolStats(), null);
        String firstTokenQuery = captureToken(first);
        String secondTokenQuery = captureToken(second);
        assertTrue(firstTokenQuery.contains("insert_deduplication_token"));
        assertTrue(secondTokenQuery.contains("insert_deduplication_token"));
        assertNotEquals(firstTokenQuery, secondTokenQuery);
        first.closeSpool();
        second.closeSpool();
    }

    private String captureToken(ClickHouseVariableHistoryStore target) {
        queries.clear();
        target.appendBatch(List.of(VariableHistoryWriteRecord.ingested(
                "root.platform.devices.sensor",
                "temperature",
                "value",
                Instant.parse("2025-06-25T12:00:00.123Z"),
                42.0,
                null
        )));
        // Cert-branch store durably appends then inserts on replay.
        target.replaySpool();
        return queries.stream()
                .filter(q -> q != null && q.contains("insert_deduplication_token"))
                .findFirst()
                .orElse("");
    }
}
