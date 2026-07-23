package com.ispf.driver.webtransaction;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against an embedded JDK HttpServer with two cooperating endpoints.
 */
class WebTransactionDeviceDriverTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> requestLog = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<String> lastPostBody = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/step1", exchange -> {
            requestLog.add(exchange.getRequestMethod() + " /step1");
            respond(exchange, 200, "step-one-ok");
        });
        server.createContext("/step2", exchange -> {
            requestLog.add(exchange.getRequestMethod() + " /step2");
            lastPostBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, "created:" + lastPostBody.get());
        });
        server.createContext("/missing", exchange -> {
            requestLog.add(exchange.getRequestMethod() + " /missing");
            respond(exchange, 404, "not-found");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void runsPipeDelimitedTwoStepTransaction() throws Exception {
        String mapping = "step1:GET:" + baseUrl + "/step1"
                + "|step2:POST:" + baseUrl + "/step2:ping";
        StubDriverObject driverObject = driverConfig(Map.of());
        WebTransactionDeviceDriver driver = new WebTransactionDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("txn", mapping));

        DataRecord record = driverObject.variables.get("txn");
        assertEquals(201, ((Number) record.firstRow().get("statusCode")).intValue());
        assertEquals("created:ping", record.firstRow().get("value"));
        assertTrue(((Number) record.firstRow().get("latencyMs")).longValue() >= 0);

        assertEquals(List.of("GET /step1", "POST /step2"), requestLog);
        assertEquals("ping", lastPostBody.get());

        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void runsJsonStepsTransaction() throws Exception {
        String mapping = "["
                + "{\"name\":\"a\",\"method\":\"GET\",\"url\":\"" + baseUrl + "/step1\"},"
                + "{\"name\":\"b\",\"method\":\"POST\",\"url\":\"" + baseUrl + "/step2\",\"body\":\"xyz\"}"
                + "]";
        StubDriverObject driverObject = driverConfig(Map.of());
        WebTransactionDeviceDriver driver = new WebTransactionDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("txn", mapping));

        DataRecord record = driverObject.variables.get("txn");
        assertEquals(201, ((Number) record.firstRow().get("statusCode")).intValue());
        assertEquals("created:xyz", record.firstRow().get("value"));
        assertEquals(List.of("GET /step1", "POST /step2"), requestLog);
        driver.disconnect();
    }

    @Test
    void usesStepsJsonConfigWhenMappingIsBlank() throws Exception {
        String stepsJson = "[{\"name\":\"only\",\"method\":\"GET\",\"url\":\"" + baseUrl + "/step1\"}]";
        StubDriverObject driverObject = driverConfig(Map.of("stepsJson", stepsJson));
        WebTransactionDeviceDriver driver = new WebTransactionDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("txn", ""));

        DataRecord record = driverObject.variables.get("txn");
        assertEquals(200, ((Number) record.firstRow().get("statusCode")).intValue());
        assertEquals("step-one-ok", record.firstRow().get("value"));
        assertEquals(List.of("GET /step1"), requestLog);
        driver.disconnect();
    }

    @Test
    void intermediateFailureDoesNotStopTransaction() throws Exception {
        // Limitation guard: no per-step assertions — a 404 mid-transaction is ignored,
        // the final step status/body is what gets reported.
        String mapping = "s1:GET:" + baseUrl + "/missing"
                + "|s2:GET:" + baseUrl + "/step1";
        StubDriverObject driverObject = driverConfig(Map.of());
        WebTransactionDeviceDriver driver = new WebTransactionDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("txn", mapping));

        DataRecord record = driverObject.variables.get("txn");
        assertEquals(200, ((Number) record.firstRow().get("statusCode")).intValue());
        assertEquals("step-one-ok", record.firstRow().get("value"));
        assertEquals(List.of("GET /missing", "GET /step1"), requestLog);
        driver.disconnect();
    }

    @Test
    void unreachableStepFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String mapping = "s1:GET:http://127.0.0.1:" + closedPort + "/down";
        WebTransactionDeviceDriver driver = new WebTransactionDeviceDriver();
        driver.initialize(driverConfig(Map.of()));
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("txn", mapping)));
        assertTrue(error.getMessage().contains("Web transaction failed"));
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        WebTransactionDeviceDriver driver = new WebTransactionDeviceDriver();
        driver.initialize(driverConfig(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("txn", "s1:GET:" + baseUrl + "/step1")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void writeIsReadOnly() {
        WebTransactionDeviceDriver driver = new WebTransactionDeviceDriver();
        driver.initialize(driverConfig(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("txn", DataRecord.single(
                        DataSchema.builder("webTransactionResult")
                                .field("statusCode", FieldType.INTEGER)
                                .field("latencyMs", FieldType.LONG)
                                .field("value", FieldType.STRING)
                                .build(),
                        Map.of("statusCode", 200, "latencyMs", 0L, "value", "")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig(Map<String, String> extra) {
        Map<String, String> configuration = new HashMap<>();
        configuration.put("timeoutMs", "5000");
        configuration.putAll(extra);
        return new StubDriverObject(configuration);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
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
                    "test-web-transaction",
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
