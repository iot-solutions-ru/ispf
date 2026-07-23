package com.ispf.driver.httpserver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerDeviceDriverTest {

    private HttpServerDeviceDriver driver;
    private StubDriverObject driverObject;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
    }

    @Test
    void servesRequestsAndExposesMetrics() throws Exception {
        startDriver("/ispf");
        String baseUrl = "http://127.0.0.1:" + listenPort() + "/ispf";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> first = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/alpha"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> second = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/beta"))
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString("payload-1"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, first.statusCode());
        assertEquals("OK", first.body());
        assertEquals(200, second.statusCode());

        driver.readPoints(Map.of(
                "requestCount", "requests",
                "lastPath", "lastPath",
                "lastBody", "lastBody"
        ));

        DataRecord requests = driverObject.variables.get("requestCount");
        assertEquals("2", requests.firstRow().get("value"));
        assertEquals(2L, ((Number) requests.firstRow().get("count")).longValue());

        DataRecord lastPath = driverObject.variables.get("lastPath");
        assertEquals("/ispf/beta", lastPath.firstRow().get("value"));

        DataRecord lastBody = driverObject.variables.get("lastBody");
        assertEquals("payload-1", lastBody.firstRow().get("value"));
        assertEquals(2L, ((Number) lastBody.firstRow().get("count")).longValue());
    }

    @Test
    void requestsOutsideContextPathAreNotCounted() throws Exception {
        startDriver("/ispf");
        int port = listenPort();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> handled = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ispf/x"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, handled.statusCode());

        HttpResponse<String> outside = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/other"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, outside.statusCode());

        driver.readPoints(Map.of("requestCount", "requests"));
        DataRecord requests = driverObject.variables.get("requestCount");
        assertEquals("1", requests.firstRow().get("value"));
    }

    @Test
    void writePointIsReadOnly() {
        driver = new HttpServerDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("requestCount", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void readPointsRequiresConnection() {
        driver = new HttpServerDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("requestCount", "requests")));
    }

    private void startDriver(String contextPath) throws DriverException {
        driverObject = new StubDriverObject(Map.of(
                "listenPort", String.valueOf(freePort()),
                "contextPath", contextPath
        ));
        driver = new HttpServerDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
    }

    private int listenPort() {
        return Integer.parseInt(driverObject.configuration().get("listenPort"));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free port available", e);
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
                    "test-http-server",
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
