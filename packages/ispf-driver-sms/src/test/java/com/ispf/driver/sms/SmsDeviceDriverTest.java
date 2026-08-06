package com.ispf.driver.sms;

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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsDeviceDriverTest {

    private HttpServer server;
    private String relayUrl;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void writePostsSmsJsonToRelay() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        startRelay(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "relayUrl", relayUrl,
                "timeoutMs", "5000"
        ));
        SmsDeviceDriver driver = new SmsDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("out", "sms"));

        driver.writePoint("out", DataRecord.single(
                DataSchema.builder("payload")
                        .field("to", FieldType.STRING)
                        .field("text", FieldType.STRING)
                        .build(),
                Map.of("to", "+15551234567", "text", "Site down")
        ));

        assertTrue(body.get().contains("+15551234567"));
        assertTrue(body.get().contains("Site down"));
        assertEquals("sent", driverObject.variables.get("out").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void connectRequiresRelayUrl() {
        SmsDeviceDriver driver = new SmsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, driver::connect);
    }

    private void startRelay(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sms", handler);
        server.start();
        relayUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-sms", "root.platform.devices.sms", ObjectType.DEVICE, "Test", "", null);
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
