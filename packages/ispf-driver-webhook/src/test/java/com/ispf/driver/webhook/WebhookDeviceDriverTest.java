package com.ispf.driver.webhook;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookDeviceDriverTest {

    private HttpServer server;
    private String targetUrl;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void writePostsJsonPayload() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        startHook(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "targetUrl", targetUrl,
                "timeoutMs", "5000"
        ));
        WebhookDeviceDriver driver = new WebhookDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("out", "webhook"));

        driver.writePoint("out", DataRecord.single(
                DataSchema.builder("payload")
                        .field("eventName", FieldType.STRING)
                        .field("severity", FieldType.STRING)
                        .build(),
                Map.of("eventName", "thresholdExceeded", "severity", "high")
        ));

        assertTrue(body.get().contains("thresholdExceeded"));
        assertTrue(body.get().contains("high"));
        assertEquals("sent", driverObject.variables.get("out").firstRow().get("value"));
        driver.disconnect();
    }

    private void startHook(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", handler);
        server.start();
        targetUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-webhook", "root.platform.devices.webhook", ObjectType.DEVICE, "Test", "", null);
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
