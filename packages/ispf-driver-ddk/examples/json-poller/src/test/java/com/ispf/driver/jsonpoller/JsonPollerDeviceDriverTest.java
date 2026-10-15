package com.ispf.driver.jsonpoller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPollerDeviceDriverTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    @Test
    void readFetchesJsonAndExtractsPath() throws Exception {
        String payload = """
                {"data":{"temp":21.5,"unit":"C"}}
                """;
        int port = startJsonServer(payload);
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "baseUrl", "http://127.0.0.1:" + port + "/telemetry",
                "timeoutMs", "3000"
        ));
        JsonPollerDeviceDriver driver = new JsonPollerDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", "jsonPath:$.data.temp"));

        DataRecord record = driverObject.variables.get("temperature");
        assertEquals("$.data.temp", record.firstRow().get("jsonPath"));
        assertEquals("21.5", record.firstRow().get("value"));
        assertEquals(true, record.firstRow().get("connected"));
        assertTrue(record.firstRow().get("raw").toString().contains("temp"));
        driver.disconnect();
    }

    @Test
    void extractPathHandlesNestedObjects() {
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.putObject("data").put("temp", 18);
        assertEquals("18", JsonPollerDeviceDriver.extractPath(root, "$.data.temp"));
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(IllegalArgumentException.class, () -> JsonPollerPoint.parse("bad"));
    }

    private int startJsonServer(String body) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/telemetry", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        httpServer.setExecutor(Executors.newSingleThreadExecutor());
        httpServer.start();
        return httpServer.getAddress().getPort();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> config;
        private final Map<String, DataRecord> variables = new HashMap<>();

        private StubDriverObject(Map<String, String> config) {
            this.config = config;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("json-poller", "root.platform.devices.json-poller", ObjectType.DEVICE, "JSON Poller", "", null);
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
            return config;
        }
    }
}
