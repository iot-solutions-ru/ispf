package com.ispf.driver.smis;

import com.ispf.core.model.DataRecord;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmisDeviceDriverTest {

    private HttpServer server;
    private int port;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void enumeratesProfilesViaLoopbackHttp() throws Exception {
        startCimServer("""
                <CIM CIMVERSION="2.0">
                  <MESSAGE>
                    <SIMPLERSP>
                      <IMETHODRESPONSE NAME="EnumerateInstances">
                        <IRETURNVALUE>
                          <VALUE.OBJECT>
                            <INSTANCE CLASSNAME="CIM_RegisteredProfile">
                              <PROPERTY NAME="RegisteredOrganization"><VALUE>SNIA</VALUE></PROPERTY>
                            </INSTANCE>
                          </VALUE.OBJECT>
                        </IRETURNVALUE>
                      </IMETHODRESPONSE>
                    </SIMPLERSP>
                  </MESSAGE>
                </CIM>
                """);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "useHttp", "true",
                "namespace", "root/pg",
                "timeoutMs", "5000"
        ));

        SmisDeviceDriver driver = new SmisDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("org", "CIM_RegisteredProfile:RegisteredOrganization"));

        DataRecord record = driverObject.variables.get("org");
        assertEquals(200, record.firstRow().get("statusCode"));
        assertEquals("SNIA", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void cimErrorResponseFailsRead() throws Exception {
        startCimServer("""
                <CIM CIMVERSION="2.0">
                  <MESSAGE>
                    <SIMPLERSP>
                      <IMETHODRESPONSE NAME="EnumerateInstances">
                        <ERROR CODE="5" DESCRIPTION="CIM_ERR_INVALID_NAMESPACE unique-marker-42"/>
                      </IMETHODRESPONSE>
                    </SIMPLERSP>
                  </MESSAGE>
                </CIM>
                """);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "useHttp", "true",
                "timeoutMs", "5000"
        ));

        SmisDeviceDriver driver = new SmisDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("org", "CIM_RegisteredProfile:RegisteredOrganization")));
        assertTrue(error.getMessage().contains("5"));
        assertTrue(error.getMessage().contains("unique-marker-42"));
        assertTrue(driverObject.variables.isEmpty());
        driver.disconnect();
    }

    @Test
    void connectionRefusedFailsRead() throws Exception {
        // Bind and immediately release a port to guarantee nothing listens on it.
        int freePort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(freePort),
                "useHttp", "true",
                "timeoutMs", "3000"
        ));

        SmisDeviceDriver driver = new SmisDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("org", "CIM_RegisteredProfile:RegisteredOrganization")));
        driver.disconnect();
    }

    @Test
    void arrayPropertiesAreJoined() throws Exception {
        startCimServer("""
                <CIM CIMVERSION="2.0">
                  <MESSAGE>
                    <SIMPLERSP>
                      <IMETHODRESPONSE NAME="EnumerateInstances">
                        <IRETURNVALUE>
                          <VALUE.OBJECT>
                            <INSTANCE CLASSNAME="CIM_RegisteredProfile">
                              <PROPERTY NAME="RegisteredName"><VALUE>unique-profile-alpha</VALUE></PROPERTY>
                              <PROPERTY.ARRAY NAME="RegisteredSubprofiles">
                                <VALUE.ARRAY><VALUE>sub-one</VALUE><VALUE>sub-two</VALUE></VALUE.ARRAY>
                              </PROPERTY.ARRAY>
                            </INSTANCE>
                          </VALUE.OBJECT>
                        </IRETURNVALUE>
                      </IMETHODRESPONSE>
                    </SIMPLERSP>
                  </MESSAGE>
                </CIM>
                """);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "useHttp", "true",
                "timeoutMs", "5000"
        ));

        SmisDeviceDriver driver = new SmisDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "name", "CIM_RegisteredProfile:RegisteredName",
                "subs", "CIM_RegisteredProfile:RegisteredSubprofiles",
                "missing", "CIM_RegisteredProfile:NoSuchProperty"
        ));

        assertEquals("unique-profile-alpha", driverObject.variables.get("name").firstRow().get("value"));
        assertEquals("sub-one,sub-two", driverObject.variables.get("subs").firstRow().get("value"));
        assertEquals("NOT_AVAILABLE", driverObject.variables.get("missing").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        SmisDeviceDriver driver = new SmisDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("org", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void startCimServer(String xmlBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cimom", exchange -> respondXml(exchange, xmlBody));
        server.start();
        port = server.getAddress().getPort();
    }

    private static void respondXml(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
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
                    "test-smis",
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
