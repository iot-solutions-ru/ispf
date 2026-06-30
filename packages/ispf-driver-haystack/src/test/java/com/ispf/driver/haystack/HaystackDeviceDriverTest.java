package com.ispf.driver.haystack;

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

class HaystackDeviceDriverTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastReadBody = new AtomicReference<>("");

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void readsHaystackRefsViaLoopbackServer() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            if (path.endsWith("/about")) {
                body = """
                        {"_kind":"grid","meta":{"ver":"3.0"},"cols":[{"name":"serverName"}],"rows":[{"serverName":"loopback"}]}
                        """.getBytes(StandardCharsets.UTF_8);
            } else if (path.endsWith("/read")) {
                lastReadBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                body = """
                        {
                          "_kind": "grid",
                          "meta": {"ver": "3.0"},
                          "cols": [{"name": "id"}, {"name": "curVal"}, {"name": "unit"}, {"name": "dis"}],
                          "rows": [
                            {
                              "id": {"_kind": "ref", "val": "site.equip.supplyTemp"},
                              "curVal": 21.2,
                              "unit": "°C",
                              "dis": "Supply temp"
                            }
                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8);
            } else {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", HaystackJsonGrid.JSON_MEDIA_TYPE);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        StubDriverObject driverObject = driverConfig();
        HaystackDeviceDriver driver = new HaystackDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("supplyTemp", "@site.equip.supplyTemp"));

        assertTrue(lastReadBody.get().contains("site.equip.supplyTemp"));
        DataRecord record = driverObject.variables.get("supplyTemp");
        assertEquals(21.2, ((Number) record.firstRow().get("value")).doubleValue(), 0.001);
        assertEquals("°C", record.firstRow().get("unit"));
        assertEquals("Supply temp", record.firstRow().get("dis"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        HaystackDeviceDriver driver = new HaystackDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("supplyTemp", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.DOUBLE)
                                .build(),
                        Map.of("value", 1.0)
                )));
        assertTrue(error.getMessage().contains("read-only"));
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
                "project", "demo",
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
                    "test-haystack",
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
