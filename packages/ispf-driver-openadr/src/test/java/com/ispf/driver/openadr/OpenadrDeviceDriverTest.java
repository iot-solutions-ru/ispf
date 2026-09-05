package com.ispf.driver.openadr;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenadrDeviceDriverTest {

    private HttpServer server;
    private String vtnUrl;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private final AtomicInteger createdEventCount = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void pollsXmlDistributeEventAndReadsSignal() throws Exception {
        startXmlVtn();
        StubDriverObject object = config("application/xml");
        OpenadrDeviceDriver driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of(
                "id", "eventId",
                "level", "signalLevel",
                "name", "signalName",
                "on", "active"
        ));
        assertEquals("evt-42", object.variables.get("id").firstRow().get("value"));
        assertEquals("2", object.variables.get("level").firstRow().get("value"));
        assertEquals("SIMPLE", object.variables.get("name").firstRow().get("value"));
        assertEquals("true", object.variables.get("on").firstRow().get("value"));
        assertTrue(lastRequest.get().contains("oadrPoll"));
        assertTrue(lastRequest.get().contains("ven-lab"));
        driver.disconnect();
    }

    @Test
    void pollsJsonDistributeEventSubset() throws Exception {
        startJsonVtn();
        StubDriverObject object = new StubDriverObject(Map.of(
                "vtnUrl", vtnUrl,
                "venId", "ven-json",
                "pollMethod", "GET",
                "accept", "application/json",
                "timeoutMs", "3000"
        ));
        OpenadrDeviceDriver driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("id", "eventId", "level", "currentValue"));
        assertEquals("json-7", object.variables.get("id").firstRow().get("value"));
        assertEquals("1", object.variables.get("level").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writePostsCreatedEventOptIn() throws Exception {
        startXmlVtn();
        StubDriverObject object = config("application/xml");
        OpenadrDeviceDriver driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("id", "eventId"));
        driver.writePoint("id", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "optIn")
        ));
        assertEquals(1, createdEventCount.get());
        assertTrue(lastRequest.get().contains("oadrCreatedEvent"));
        assertTrue(lastRequest.get().contains("evt-42"));
        driver.disconnect();
    }

    @Test
    void readBeforeConnectThrows() {
        OpenadrDeviceDriver driver = new OpenadrDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("id", "eventId")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private void startXmlVtn() throws IOException {
        createdEventCount.set(0);
        lastRequest.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequest.set(body);
            String response;
            if (body.contains("oadrCreatedEvent")) {
                createdEventCount.incrementAndGet();
                response = "<?xml version=\"1.0\"?><oadr:oadrResponse xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\">"
                        + "<ei:eiResponse xmlns:ei=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                        + "<ei:responseCode>200</ei:responseCode></ei:eiResponse></oadr:oadrResponse>";
            } else {
                response = "<?xml version=\"1.0\"?>"
                        + "<oadr:oadrDistributeEvent xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\""
                        + " xmlns:ei=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                        + "<oadr:oadrEvent><ei:eiEvent>"
                        + "<ei:eventDescriptor><ei:eventID>evt-42</ei:eventID></ei:eventDescriptor>"
                        + "<ei:eiEventSignals><ei:eiEventSignal>"
                        + "<ei:signalName>SIMPLE</ei:signalName>"
                        + "<ei:currentValue>2</ei:currentValue>"
                        + "</ei:eiEventSignal></ei:eiEventSignals>"
                        + "</ei:eiEvent></oadr:oadrEvent></oadr:oadrDistributeEvent>";
            }
            respond(exchange, "application/xml", response);
        });
        server.start();
        vtnUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/OpenADR2/Simple/2.0b";
    }

    private void startJsonVtn() throws IOException {
        lastRequest.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequest.set(exchange.getRequestMethod());
            respond(exchange, "application/json",
                    "{\"oadrEvent\":{\"eventID\":\"json-7\",\"signalName\":\"SIMPLE\",\"currentValue\":1}}");
        });
        server.start();
        vtnUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/events";
    }

    private static void respond(HttpExchange exchange, String contentType, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private StubDriverObject config(String accept) {
        return new StubDriverObject(Map.of(
                "vtnUrl", vtnUrl,
                "venId", "ven-lab",
                "pollMethod", "POST",
                "accept", accept,
                "timeoutMs", "3000"
        ));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-openadr", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
