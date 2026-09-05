package com.ispf.driver.grpc;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcJsonDeviceDriverTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void unaryJsonPostExtractsMessageField() throws Exception {
        startHelloServer();
        StubDriverObject object = config();
        GrpcJsonDeviceDriver driver = new GrpcJsonDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("greeting", "helloworld.Greeter/SayHello#message"));
        DataRecord record = object.variables.get("greeting");
        assertEquals("Hello world", record.firstRow().get("value"));
        assertEquals("helloworld.Greeter/SayHello", record.firstRow().get("method"));
        assertEquals(200, record.firstRow().get("statusCode"));
        assertTrue(lastBody.get().contains("\"name\":\"world\""));
        driver.disconnect();
    }

    @Test
    void writeUpdatesRequestNameAndReinvokes() throws Exception {
        startHelloServer();
        StubDriverObject object = config();
        GrpcJsonDeviceDriver driver = new GrpcJsonDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("greeting", "helloworld.Greeter/SayHello#message"));
        driver.writePoint("greeting", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "ISPF")
        ));
        assertEquals("Hello ISPF", object.variables.get("greeting").firstRow().get("value"));
        assertTrue(lastBody.get().contains("\"name\":\"ISPF\""));
        driver.disconnect();
    }

    @Test
    void metadataIsHonestAboutLabJsonMapping() {
        GrpcJsonDeviceDriver driver = new GrpcJsonDeviceDriver();
        assertEquals("grpc", driver.metadata().id());
        assertTrue(driver.metadata().description().contains("NOT wire-compatible"));
        assertTrue(driver.metadata().name().contains("JSON"));
    }

    @Test
    void pointParseRequiresServiceMethod() {
        assertThrows(IllegalArgumentException.class, () -> GrpcJsonPoint.parse("SayHello"));
        GrpcJsonPoint point = GrpcJsonPoint.parse("pkg.Svc/Method#field");
        assertEquals("/pkg.Svc/Method", point.httpPath());
        assertEquals("field", point.field());
    }

    @Test
    void readBeforeConnectThrows() {
        GrpcJsonDeviceDriver driver = new GrpcJsonDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("g", "Greeter/SayHello#message")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private void startHelloServer() throws IOException {
        lastBody.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/helloworld.Greeter/SayHello", this::handleHello);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handleHello(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastBody.set(body);
        String name = "world";
        int idx = body.indexOf("\"name\"");
        if (idx >= 0) {
            int colon = body.indexOf(':', idx);
            int q1 = body.indexOf('"', colon + 1);
            int q2 = body.indexOf('"', q1 + 1);
            if (q1 >= 0 && q2 > q1) {
                name = body.substring(q1 + 1, q2);
            }
        }
        byte[] response = ("{\"message\":\"Hello " + name + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private StubDriverObject config() {
        return new StubDriverObject(Map.of("baseUrl", baseUrl, "timeoutMs", "3000", "defaultName", "world"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-grpc", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
