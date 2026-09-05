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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link OpenadrDeviceDriver} against an in-process fake VTN.
 */
class OpenadrDeviceDriverTest {

    private OpenadrDeviceDriver driver;
    private HttpServer server;
    private String vtnUrl;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
    private final AtomicInteger createdEventPosts = new AtomicInteger();
    private final AtomicReference<String> lastOptType = new AtomicReference<>("");

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
    void pollsDistributeEventAndReadsPoints() throws Exception {
        startServer(sampleDistributeEventXml());
        StubDriverObject object = new StubDriverObject(Map.of(
                "vtnUrl", vtnUrl,
                "venId", "ven-lab-1",
                "pollMethod", "POST",
                "accept", "application/xml",
                "timeoutMs", "2000"
        ));
        driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "eid", "eventId",
                "level", "signalLevel",
                "name", "signalName",
                "on", "active"
        ));

        assertEquals("EVT-100", object.variables.get("eid").firstRow().get("value"));
        assertEquals("eventId", object.variables.get("eid").firstRow().get("kind"));
        assertEquals(200, object.variables.get("eid").firstRow().get("statusCode"));
        assertEquals("3", object.variables.get("level").firstRow().get("value"));
        assertEquals("SIMPLE", object.variables.get("name").firstRow().get("value"));
        assertEquals("true", object.variables.get("on").firstRow().get("value"));
        assertTrue(lastRequestBody.get().contains("oadrPoll"));
        assertTrue(lastRequestBody.get().contains("ven-lab-1"));
    }

    @Test
    void writesCreatedEventOptIn() throws Exception {
        startServer(sampleDistributeEventXml());
        StubDriverObject object = new StubDriverObject(Map.of(
                "vtnUrl", vtnUrl,
                "venId", "ven-lab-1",
                "accept", "application/xml"
        ));
        driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("eid", "eventId"));

        driver.writePoint("eid", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "optIn")
        ));

        assertEquals(1, createdEventPosts.get());
        assertEquals("optIn", lastOptType.get());
        assertEquals("optIn", object.variables.get("eid").firstRow().get("value"));
        assertEquals(200, object.variables.get("eid").firstRow().get("statusCode"));
    }

    @Test
    void getPollJsonPayload() throws Exception {
        startServer("""
                {"eventID":"J-1","signalName":"LOAD_CONTROL","currentValue":"2.5"}
                """);
        StubDriverObject object = new StubDriverObject(Map.of(
                "vtnUrl", vtnUrl,
                "pollMethod", "GET",
                "accept", "application/json"
        ));
        driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of(
                "eid", "eventId",
                "level", "currentValue"
        ));
        assertEquals("J-1", object.variables.get("eid").firstRow().get("value"));
        assertEquals("2.5", object.variables.get("level").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new OpenadrDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("eid", "eventId")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writeWithoutPriorEventThrows() throws Exception {
        startServer("");
        StubDriverObject object = new StubDriverObject(Map.of("vtnUrl", vtnUrl));
        driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("eid", "eventId"));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("eid", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.STRING).build(),
                        Map.of("value", "optIn")
                )));
        assertTrue(error.getMessage().contains("No active OpenADR event"));
    }

    @Test
    void pointParseForms() {
        assertEquals("eventId", OpenadrPoint.parse("eventId").kind());
        assertEquals("signalLevel", OpenadrPoint.parse("currentValue").kind());
        assertEquals("signalName", OpenadrPoint.parse("signalName").kind());
        assertThrows(IllegalArgumentException.class, () -> OpenadrPoint.parse("bogus"));
    }

    private void startServer(String pollResponse) throws IOException {
        lastRequestBody.set("");
        createdEventPosts.set(0);
        lastOptType.set("");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, pollResponse));
        server.start();
        vtnUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/OpenADR2/Simple/2.0b";
    }

    private void handle(HttpExchange exchange, String pollResponse) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastRequestBody.set(body);
        String response;
        if (body.contains("oadrCreatedEvent") || body.contains("\"oadrCreatedEvent\"")) {
            createdEventPosts.incrementAndGet();
            if (body.contains("<ei:optType>")) {
                int start = body.indexOf("<ei:optType>") + "<ei:optType>".length();
                int end = body.indexOf("</ei:optType>");
                if (end > start) {
                    lastOptType.set(body.substring(start, end));
                }
            } else if (body.contains("\"optType\"")) {
                int idx = body.indexOf("\"optType\"");
                int colon = body.indexOf(':', idx);
                int q1 = body.indexOf('"', colon + 1);
                int q2 = body.indexOf('"', q1 + 1);
                if (q1 >= 0 && q2 > q1) {
                    lastOptType.set(body.substring(q1 + 1, q2));
                }
            }
            response = "<oadr:oadrResponse>OK</oadr:oadrResponse>";
        } else {
            response = pollResponse == null ? "" : pollResponse;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String sampleDistributeEventXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <oadr:oadrPayload xmlns:oadr="http://openadr.org/oadr-2.0b/2012/07"
                                  xmlns:ei="http://docs.oasis-open.org/ns/energyinterop/201110">
                  <oadr:oadrSignedObject>
                    <oadr:oadrDistributeEvent>
                      <ei:eventID>EVT-100</ei:eventID>
                      <ei:signalName>SIMPLE</ei:signalName>
                      <ei:currentValue>3</ei:currentValue>
                    </oadr:oadrDistributeEvent>
                  </oadr:oadrSignedObject>
                </oadr:oadrPayload>
                """;
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-openadr",
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
