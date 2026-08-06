package com.ispf.driver.http;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDeviceDriverTest {

    private HttpServer server;
    private String baseUrl;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void readsGetEndpointViaLoopbackServer() throws Exception {
        startServer(exchange -> {
            byte[] body = "1.2.3".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        StubDriverObject driverObject = driverConfig();
        HttpDeviceDriver driver = new HttpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("info", "GET:/api/v1/info"));

        DataRecord record = driverObject.variables.get("info");
        assertEquals(200, record.firstRow().get("statusCode"));
        assertEquals("1.2.3", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writesPostBodyToMappedUrl() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        startServer(exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        StubDriverObject driverObject = driverConfig();
        HttpDeviceDriver driver = new HttpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("relay", "GET:/health"));

        driver.writePoint("relay", DataRecord.single(
                com.ispf.core.model.DataSchema.builder("payload")
                        .field("to", com.ispf.core.model.FieldType.STRING)
                        .field("subject", com.ispf.core.model.FieldType.STRING)
                        .field("body", com.ispf.core.model.FieldType.STRING)
                        .build(),
                Map.of("to", "noc@example.com", "subject", "Alert", "body", "Threshold")
        ));

        assertEquals("POST", method.get());
        assertTrue(contentType.get().contains("application/json"));
        assertTrue(body.get().contains("noc@example.com"));
        assertTrue(body.get().contains("Alert"));
        assertEquals(204, driverObject.variables.get("relay").firstRow().get("statusCode"));
        driver.disconnect();
    }

    @Test
    void writeUsesWritePathWhenConfigured() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "baseUrl", baseUrl,
                "timeoutMs", "5000",
                "writePath", "/v1/email"
        ));
        HttpDeviceDriver driver = new HttpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("health", "GET:/health"));

        driver.writePoint("health", DataRecord.single(
                com.ispf.core.model.DataSchema.builder("payload")
                        .field("value", com.ispf.core.model.FieldType.STRING)
                        .build(),
                Map.of("value", "{\"to\":\"a@b.c\",\"subject\":\"s\",\"body\":\"b\"}")
        ));

        assertEquals("/v1/email", path.get());
        driver.disconnect();
    }

    @Test
    void writeRequiresConnection() {
        HttpDeviceDriver driver = new HttpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("info", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private StubDriverObject driverConfig() {
        return new StubDriverObject(Map.of(
                "baseUrl", baseUrl,
                "timeoutMs", "5000"
        ));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-http",
                    "root.platform.devices.test",
                    ObjectType.DEVICE,
                    "Test",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
