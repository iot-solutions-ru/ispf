package com.ispf.driver.soap;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against an embedded JDK HttpServer acting as the SOAP endpoint.
 */
class SoapDeviceDriverTest {

    private static final String REQUEST_ENVELOPE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wx="http://example.com/weather">
              <soapenv:Header/>
              <soapenv:Body>
                <wx:GetTemperature><wx:City>Berlin</wx:City></wx:GetTemperature>
              </soapenv:Body>
            </soapenv:Envelope>""";

    private static final String RESPONSE_ENVELOPE = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Body><GetTemperatureResponse><value>21</value></GetTemperatureResponse></soapenv:Body>
            </soapenv:Envelope>""";

    private static final String FAULT_BODY = "<soap:Fault><faultstring>internal</faultstring></soap:Fault>";

    private HttpServer server;
    private String endpointUrl;
    private String faultEndpointUrl;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
    private final AtomicReference<String> lastSoapAction = new AtomicReference<>(null);
    private final AtomicReference<String> lastContentType = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/soap", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastSoapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 200, RESPONSE_ENVELOPE);
        });
        server.createContext("/fault", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 500, FAULT_BODY);
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        endpointUrl = base + "/soap";
        faultEndpointUrl = base + "/fault";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void postsEnvelopeAndMapsResponse() throws Exception {
        StubDriverObject driverObject = driverConfig(Map.of("soapAction", "urn:GetTemperature"));
        SoapDeviceDriver driver = new SoapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("getTemp", REQUEST_ENVELOPE));

        assertEquals(REQUEST_ENVELOPE, lastRequestBody.get());
        assertEquals("urn:GetTemperature", lastSoapAction.get());
        assertTrue(lastContentType.get().startsWith("text/xml"), "Content-Type was " + lastContentType.get());

        DataRecord record = driverObject.variables.get("getTemp");
        assertEquals(RESPONSE_ENVELOPE, record.firstRow().get("value"));
        assertEquals(200, ((Number) record.firstRow().get("statusCode")).intValue());

        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void omitsSoapActionHeaderWhenNotConfigured() throws Exception {
        StubDriverObject driverObject = driverConfig(Map.of());
        SoapDeviceDriver driver = new SoapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("getTemp", REQUEST_ENVELOPE));

        assertEquals(REQUEST_ENVELOPE, lastRequestBody.get());
        assertNull(lastSoapAction.get(), "SOAPAction header must be absent when not configured");
        driver.disconnect();
    }

    @Test
    void mapsHttpErrorStatusWithoutThrowing() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "endpointUrl", faultEndpointUrl,
                "timeoutMs", "5000"
        ));
        SoapDeviceDriver driver = new SoapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("getTemp", REQUEST_ENVELOPE));

        DataRecord record = driverObject.variables.get("getTemp");
        assertEquals(500, ((Number) record.firstRow().get("statusCode")).intValue());
        assertEquals(FAULT_BODY, record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void blankEnvelopeMappingIsRejected() throws Exception {
        SoapDeviceDriver driver = new SoapDeviceDriver();
        driver.initialize(driverConfig(Map.of()));
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("getTemp", "  ")));
        assertTrue(error.getMessage().contains("blank"));
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        SoapDeviceDriver driver = new SoapDeviceDriver();
        driver.initialize(driverConfig(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("getTemp", REQUEST_ENVELOPE)));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void unreachableEndpointFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        SoapDeviceDriver driver = new SoapDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "endpointUrl", "http://127.0.0.1:" + closedPort + "/soap",
                "timeoutMs", "5000"
        )));
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("getTemp", REQUEST_ENVELOPE)));
        assertTrue(error.getMessage().contains("SOAP request failed"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        SoapDeviceDriver driver = new SoapDeviceDriver();
        driver.initialize(driverConfig(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("getTemp", DataRecord.single(
                        DataSchema.builder("soapResponse")
                                .field("value", FieldType.STRING)
                                .field("statusCode", FieldType.INTEGER)
                                .build(),
                        Map.of("value", "", "statusCode", 200)
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig(Map<String, String> extra) {
        Map<String, String> configuration = new HashMap<>();
        configuration.put("endpointUrl", endpointUrl);
        configuration.put("timeoutMs", "5000");
        configuration.putAll(extra);
        return new StubDriverObject(configuration);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
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
                    "test-soap",
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
