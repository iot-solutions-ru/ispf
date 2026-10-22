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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Warm-tier and dual-write secondary must bootstrap CREATE DATABASE against {@code default},
 * matching {@link ClickHouseVariableHistoryStore}.
 */
class ClickHouseSchemaBootstrapDatabaseDefaultTest {

    private HttpServer server;
    private int port;
    private final CopyOnWriteArrayList<String> queries = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            byte[] response = "Ok.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void warmTierCreateDatabaseUsesDefaultDatabaseParam() {
        HistorianTierProperties tiers = new HistorianTierProperties();
        tiers.warmTier().setEnabled(true);
        tiers.warmTier().setStore("clickhouse");
        tiers.warmTier().getClickhouse().setUrl("http://localhost:" + port);
        tiers.warmTier().getClickhouse().setDatabase("ispf_missing");

        new ClickHouseWarmTierWriteStore(tiers).ensureSchema();

        assertTrue(
                queries.stream().anyMatch(q ->
                        q.contains("CREATE+DATABASE") && q.contains("database=default")),
                "warm-tier CREATE DATABASE must use database=default"
        );
    }

    @Test
    void secondaryCreateDatabaseUsesDefaultDatabaseParam() {
        VariableHistoryProperties properties = new VariableHistoryProperties();
        properties.setStore("clickhouse");
        properties.setDualWriteEnabled(true);
        properties.getClickhouse().setUrl("http://localhost:" + port);
        properties.getClickhouse().setDatabase("ispf_missing");

        new ClickHouseVariableHistorySecondaryWriteStore(properties).ensureSchema();

        assertTrue(
                queries.stream().anyMatch(q ->
                        q.contains("CREATE+DATABASE") && q.contains("database=default")),
                "dual-write secondary CREATE DATABASE must use database=default"
        );
    }
}
