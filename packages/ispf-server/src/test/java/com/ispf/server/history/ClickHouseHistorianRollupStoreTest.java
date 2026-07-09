package com.ispf.server.history;

import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickHouseHistorianRollupStoreTest {

    private HttpServer server;
    private int port;
    private final CopyOnWriteArrayList<String> queries = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private ClickHouseHistorianRollupStore store;

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

        VariableHistoryProperties historyProperties = new VariableHistoryProperties();
        historyProperties.setStore("clickhouse");
        historyProperties.getClickhouse().setUrl("http://localhost:" + port);

        HistorianTierProperties tierProperties = new HistorianTierProperties();
        store = new ClickHouseHistorianRollupStore(historyProperties, tierProperties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ensureSchemaCreatesRollupTable() {
        store.ensureSchema();

        assertTrue(queries.stream().anyMatch(q -> q.contains("CREATE+TABLE")));
        assertTrue(queries.stream().anyMatch(q -> q.contains("variable_rollups")));
    }

    @Test
    void insertBucketsPostsJsonEachRowPayload() {
        store.ensureSchema();
        HistorianRollupSubscription subscription = new HistorianRollupSubscription(
                "root.platform.devices.sensor",
                "temperature",
                "value",
                Duration.ofMinutes(5)
        );
        store.insertBuckets(
                subscription,
                List.of(new VariableHistoryService.VariableHistoryBucket(
                        Instant.parse("2026-07-09T06:00:00Z"),
                        21.5,
                        20.0,
                        22.0,
                        3
                ))
        );

        assertTrue(queries.stream().anyMatch(q -> q.contains("INSERT+INTO+ispf.variable_rollups")));
        assertTrue(lastBody.get().contains("\"object_path\":\"root.platform.devices.sensor\""));
        assertTrue(lastBody.get().contains("\"avg_val\":21.5"));
    }
}
