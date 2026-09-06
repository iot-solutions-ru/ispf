package com.ispf.driver.odata;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdataDeviceDriverTest {

    private HttpServer server;
    private String baseUrl;
    private final Map<String, String> patched = new ConcurrentHashMap<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void readsEntitySetPropertyFromValueArray() throws Exception {
        startServer();
        StubDriverObject object = config();
        OdataDeviceDriver driver = new OdataDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("temp", "Sensors#Temperature"));
        DataRecord record = object.variables.get("temp");
        assertEquals("23.5", record.firstRow().get("value"));
        assertEquals("/Sensors", record.firstRow().get("path"));
        assertEquals(200, record.firstRow().get("statusCode"));
        driver.disconnect();
    }

    @Test
    void readsScalarValueProperty() throws Exception {
        startServer();
        StubDriverObject object = config();
        OdataDeviceDriver driver = new OdataDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("name", "Sensors(1)/Name"));
        assertEquals("Line-A", object.variables.get("name").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void readsWholeValueArrayAsJson() throws Exception {
        startServer();
        StubDriverObject object = config();
        OdataDeviceDriver driver = new OdataDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("all", "Sensors"));
        String value = String.valueOf(object.variables.get("all").firstRow().get("value"));
        assertTrue(value.contains("Temperature"));
        assertTrue(value.startsWith("["));
        driver.disconnect();
    }

    @Test
    void patchesPropertyViaWrite() throws Exception {
        startServer();
        StubDriverObject object = config();
        OdataDeviceDriver driver = new OdataDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("temp", "Sensors#Temperature"));
        driver.writePoint("temp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")
        ));
        assertEquals("{\"Temperature\":\"24.1\"}", patched.get("/Sensors"));
        assertEquals("24.1", object.variables.get("temp").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void readBeforeConnectThrows() {
        OdataDeviceDriver driver = new OdataDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "Sensors")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void pointParseRequiresPropertyWhenHashPresent() {
        assertThrows(IllegalArgumentException.class, () -> OdataPoint.parse("Sensors#"));
        OdataPoint point = OdataPoint.parse("Telemetry#Humidity");
        assertEquals("/Telemetry", point.path());
        assertEquals("Humidity", point.property());
    }

    private void startServer() throws IOException {
        patched.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/odata";
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            patched.put(path.startsWith("/odata") ? path.substring("/odata".length()) : path, body);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        String json;
        if (path.endsWith("/Sensors") || path.equals("/odata/Sensors")) {
            json = "{\"@odata.context\":\"$metadata#Sensors\",\"value\":[{\"Id\":1,\"Temperature\":23.5,\"Name\":\"Line-A\"}]}";
        } else if (path.contains("Sensors(1)/Name")) {
            json = "{\"@odata.context\":\"$metadata#Sensors(1)/Name\",\"value\":\"Line-A\"}";
        } else {
            byte[] body = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            return;
        }
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private StubDriverObject config() {
        return new StubDriverObject(Map.of("baseUrl", baseUrl, "timeoutMs", "3000"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-odata", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
