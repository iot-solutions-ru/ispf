package com.ispf.driver.graphql;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loopback tests for {@link GraphqlDeviceDriver} against an in-process fake GraphQL HTTP server. */
class GraphqlDeviceDriverTest {

    private HttpServer server;
    private String endpoint;
    private GraphqlDeviceDriver driver;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void queryDocumentAndFieldPathViaLoopback() throws Exception {
        AtomicReference<String> lastBody = new AtomicReference<>();
        startServer(requestBody -> {
            lastBody.set(requestBody);
            return "{\"data\":{\"sensor\":{\"temperature\":23.5,\"unit\":\"C\"}}}";
        });

        StubDriverObject object = new StubDriverObject(Map.of(
                "endpoint", endpoint,
                "timeoutMs", "2000",
                "query", "{ sensor { temperature unit } }"
        ));
        driver = new GraphqlDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "raw", "{ sensor { temperature unit } }",
                "temp", "sensor.temperature"
        ));

        assertTrue(lastBody.get().contains("sensor"));
        String raw = String.valueOf(object.variables.get("raw").firstRow().get("value"));
        assertTrue(raw.contains("temperature"));
        assertEquals("23.5", object.variables.get("temp").firstRow().get("value"));
        assertEquals("sensor.temperature", object.variables.get("temp").firstRow().get("path"));
        assertEquals(200, object.variables.get("temp").firstRow().get("statusCode"));
    }

    @Test
    void documentWithEmbeddedFieldPath() throws Exception {
        startServer(requestBody -> "{\"data\":{\"plant\":{\"status\":\"RUN\"}}}");

        StubDriverObject object = new StubDriverObject(Map.of(
                "endpoint", endpoint,
                "timeoutMs", "2000"
        ));
        driver = new GraphqlDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of(
                "status", "{ plant { status } } >> plant.status"
        ));
        assertEquals("RUN", object.variables.get("status").firstRow().get("value"));
    }

    @Test
    void writePostsMutation() throws Exception {
        AtomicReference<String> lastBody = new AtomicReference<>();
        startServer(requestBody -> {
            lastBody.set(requestBody);
            return "{\"data\":{\"setTemp\":{\"ok\":true,\"value\":24.1}}}";
        });

        StubDriverObject object = new StubDriverObject(Map.of(
                "endpoint", endpoint,
                "timeoutMs", "2000"
        ));
        driver = new GraphqlDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of(
                "temp", "mutation($value: Float!){ setTemp(value: $value){ ok value } } >> setTemp.value"
        ));

        driver.writePoint("temp", DataRecord.single(
                DataSchema.builder("m").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 24.1)
        ));

        assertTrue(lastBody.get().contains("mutation"));
        assertTrue(lastBody.get().contains("\"variables\""));
        assertTrue(lastBody.get().contains("24.1"));
        assertEquals("24.1", object.variables.get("temp").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new GraphqlDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("k", "{ __typename }")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void parseRejectsBlankMapping() {
        assertThrows(IllegalArgumentException.class, () -> GraphqlPoint.parse("  ", ""));
    }

    private void startServer(ResponseFactory factory) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            String requestBody = new String(request, StandardCharsets.UTF_8);
            byte[] body = factory.respond(requestBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/graphql";
    }

    @FunctionalInterface
    private interface ResponseFactory {
        String respond(String requestBody);
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-graphql",
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
