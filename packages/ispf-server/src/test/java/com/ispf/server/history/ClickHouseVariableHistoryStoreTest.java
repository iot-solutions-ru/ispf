package com.ispf.server.history;

import com.ispf.server.config.VariableHistoryProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
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
    }

    @Test
    void ensureSchemaCreatesDatabaseAndTable() {
        store.ensureSchema();

        assertTrue(queries.stream().anyMatch(q -> q.contains("CREATE+DATABASE")));
        assertTrue(queries.stream().anyMatch(q -> q.contains("CREATE+TABLE")));
    }

    @Test
    void supportsApplicationRetentionPurgeIsFalse() {
        assertEquals(false, store.supportsApplicationRetentionPurge());
    }
}
