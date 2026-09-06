package com.ispf.driver.plcnext;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link PlcnextDeviceDriver} against a fake RSC-lab HTTP/JSON server.
 */
class PlcnextDeviceDriverTest {

    private static final String SYMBOL = "Arp.Plc.Eclr/MainInstance.xMotor";

    private PlcnextDeviceDriver driver;
    private FakeRscLabServer server;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void metadataIsProductionReadWriteRscLab() {
        PlcnextDeviceDriver underTest = new PlcnextDeviceDriver();
        assertEquals("plcnext", underTest.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, underTest.metadata().maturity());
        assertEquals(Set.of("read", "write"), underTest.metadata().capabilities());
        assertEquals("41100", underTest.metadata().configurationSchema().get("port"));
        String description = underTest.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("rsc-lab") || description.contains("http/json"));
        assertTrue(description.contains("not full"));
    }

    @Test
    void readsAndWritesSymbolViaHttpJsonLoopback() throws Exception {
        server = new FakeRscLabServer();
        server.put(SYMBOL, "1");
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new PlcnextDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("motor", SYMBOL));
        assertEquals("1", object.variables.get("motor").firstRow().get("value"));
        assertEquals(SYMBOL, object.variables.get("motor").firstRow().get("path"));

        driver.writePoint("motor", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "true")
        ));
        assertEquals("true", server.get(SYMBOL));
        assertEquals("true", object.variables.get("motor").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsSymbolPaths() {
        assertEquals(new PlcnextPoint(SYMBOL), PlcnextPoint.parse(SYMBOL));
        assertEquals(
                new PlcnextPoint("Application.GVL.Speed"),
                PlcnextPoint.parse("Application.GVL.Speed")
        );
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new PlcnextDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", SYMBOL)));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeRscLabServer implements AutoCloseable {

        private final HttpServer httpServer;
        private final Map<String, String> storage = new ConcurrentHashMap<>();

        FakeRscLabServer() throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpServer.createContext("/rsc/variables", this::handle);
            httpServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "fake-plcnext-rsc-lab");
                thread.setDaemon(true);
                return thread;
            }));
        }

        int port() {
            return httpServer.getAddress().getPort();
        }

        void put(String path, String value) {
            storage.put(path, value);
        }

        String get(String path) {
            return storage.get(path);
        }

        void start() {
            httpServer.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getRawQuery();
                String path = queryParam(query, "path");
                String value = storage.getOrDefault(path, "");
                respond(exchange, 200, PlcnextJson.object(path, value));
                return;
            }
            if ("PUT".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String path = PlcnextJson.extractStringField(body, "path");
                String value = PlcnextJson.extractStringField(body, "value");
                storage.put(path, value);
                respond(exchange, 200, PlcnextJson.object(path, value));
                return;
            }
            respond(exchange, 405, "{\"error\":\"method not allowed\"}");
        }

        private static String queryParam(String query, String name) {
            if (query == null || query.isBlank()) {
                return "";
            }
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                if (name.equals(key)) {
                    return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                }
            }
            return "";
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        @Override
        public void close() {
            httpServer.stop(0);
        }
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
                    "test-plcnext",
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
