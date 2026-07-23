package com.ispf.driver.graphdb;

import com.ispf.core.model.DataRecord;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphDbDeviceDriverTest {

    private static final String GREMLIN_RESPONSE = """
            {"requestId":"41d2c0f5-4d9b-4b0d-9a2f-1e2f3a4b5c6d","status":{"message":"","code":200,"attributes":{"@type":"g:Map","@value":[]}},"result":{"data":{"@type":"g:List","@value":[3]},"meta":{"@type":"g:Map","@value":[]}}}
            """;

    private HttpServer server;
    private GraphDbDeviceDriver driver;
    private StubDriverObject driverObject;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>("");
    private final AtomicReference<String> lastContentType = new AtomicReference<>("");

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
    void gremlinHttpQueryReturnsResponseBodyAsValue() throws Exception {
        startGremlinStub();
        startDriver("tester", "s3cret");

        driver.readPoints(Map.of("nodeCount", "g.V().count()"));

        // The driver exposes the raw Gremlin JSON response body as `value` (no scalar extraction).
        DataRecord record = driverObject.variables.get("nodeCount");
        assertEquals(GREMLIN_RESPONSE, record.firstRow().get("value"));
        assertTrue(((String) record.firstRow().get("value")).contains("\"@value\":[3]"));

        // The request posted to the stub carries the Gremlin script as JSON payload.
        assertTrue(lastRequestBody.get().contains("\"gremlin\":\"g.V().count()\""));
        assertEquals("application/json", lastContentType.get());
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("tester:s3cret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuth, lastAuthorization.get());
    }

    @Test
    void gremlinHttpWithoutCredentialsSendsNoAuthorizationHeader() throws Exception {
        startGremlinStub();
        startDriver("", "");

        driver.readPoints(Map.of("nodeCount", "g.V().count()"));

        assertEquals(GREMLIN_RESPONSE, driverObject.variables.get("nodeCount").firstRow().get("value"));
        assertEquals("", lastAuthorization.get());
    }

    @Test
    void writePointIsReadOnly() {
        driver = new GraphDbDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("nodeCount", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void readPointsRequiresConnection() {
        driver = new GraphDbDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("nodeCount", "g.V().count()")));
    }

    private void startGremlinStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gremlin", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastAuthorization.set(authorization == null ? "" : authorization);
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            lastContentType.set(contentType == null ? "" : contentType);
            byte[] body = GREMLIN_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    private void startDriver(String username, String password) throws DriverException {
        driverObject = new StubDriverObject(Map.of(
                "uri", "http://127.0.0.1:" + server.getAddress().getPort() + "/gremlin",
                "username", username,
                "password", password,
                "timeoutMs", "5000"
        ));
        driver = new GraphDbDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
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
                    "test-graph-db",
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
