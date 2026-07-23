package com.ispf.driver.vmware;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VmwareDeviceDriverTest {

    private HttpServer server;
    private String hostWithPort;
    private final AtomicInteger loginCount = new AtomicInteger();
    private final AtomicInteger logoutCount = new AtomicInteger();
    private volatile boolean rejectFirstRetrieve = true;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void logsInAndRetrievesPropertiesViaLoopbackSoap() throws Exception {
        startVsphereServer();
        rejectFirstRetrieve = false;

        StubDriverObject driverObject = new StubDriverObject(baseConfig());
        VmwareDeviceDriver driver = new VmwareDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "version", "about.version",
                "fullName", "about.fullName",
                "connected", "connected"
        ));

        // Values come from RetrieveProperties (rp-*), not RetrieveServiceContent (sc-*).
        assertEquals("rp-version-unique-777", driverObject.variables.get("version").firstRow().get("value"));
        assertEquals("rp-fullname-fake-lab", driverObject.variables.get("fullName").firstRow().get("value"));
        assertEquals("true", driverObject.variables.get("connected").firstRow().get("value"));
        assertEquals(1, loginCount.get());

        driver.disconnect();
        assertEquals(1, logoutCount.get());
    }

    @Test
    void reLogsInOnNotAuthenticatedFault() throws Exception {
        startVsphereServer();
        rejectFirstRetrieve = true;

        StubDriverObject driverObject = new StubDriverObject(baseConfig());
        VmwareDeviceDriver driver = new VmwareDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("version", "about.version"));

        assertEquals("rp-version-unique-777", driverObject.variables.get("version").firstRow().get("value"));
        assertEquals(2, loginCount.get());
        driver.disconnect();
    }

    @Test
    void connectionRefusedFailsConnect() throws Exception {
        int freePort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        VmwareDeviceDriver driver = new VmwareDeviceDriver();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1:" + freePort,
                "useHttp", "true",
                "timeoutMs", "3000"
        ));
        driver.initialize(driverObject);

        assertThrows(DriverException.class, driver::connect);
    }

    @Test
    void writeIsReadOnly() {
        VmwareDeviceDriver driver = new VmwareDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("version", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private Map<String, String> baseConfig() {
        return Map.of(
                "host", hostWithPort,
                "username", "administrator@vsphere.local",
                "password", "secret",
                "useHttp", "true",
                "timeoutMs", "5000"
        );
    }

    private void startVsphereServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/sdk", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request.contains("RetrieveServiceContent")) {
                respond(exchange, 200, """
                        <RetrieveServiceContentResponse xmlns="urn:vim25">
                          <returnval>
                            <about>
                              <fullName>sc-fullname-should-not-win</fullName>
                              <version>sc-version-000</version>
                            </about>
                            <sessionManager type="SessionManager">SessionManager</sessionManager>
                            <propertyCollector type="PropertyCollector">propertyCollector</propertyCollector>
                          </returnval>
                        </RetrieveServiceContentResponse>
                        """, null);
            } else if (request.contains("<vim:Login")) {
                loginCount.incrementAndGet();
                respond(exchange, 200, """
                        <LoginResponse xmlns="urn:vim25">
                          <returnval><key>fake-session</key><userName>administrator@vsphere.local</userName></returnval>
                        </LoginResponse>
                        """, "vmware_soap_session=\"session-abc-123\"; Path=/");
            } else if (request.contains("RetrieveProperties")) {
                String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                boolean authenticated = cookie != null && cookie.contains("session-abc-123");
                if (!authenticated || (rejectFirstRetrieve && loginCount.get() == 1)) {
                    rejectFirstRetrieve = false;
                    respond(exchange, 500, """
                            <soapenv:Fault>
                              <faultcode>soapenv:Server.userException</faultcode>
                              <faultstring>The session is not authenticated</faultstring>
                              <detail><fault xmlns="urn:vim25" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="NotAuthenticated"/></detail>
                            </soapenv:Fault>
                            """, null);
                } else {
                    respond(exchange, 200, """
                            <RetrievePropertiesResponse xmlns="urn:vim25">
                              <returnval>
                                <obj type="ServiceInstance">ServiceInstance</obj>
                                <propSet><name>content.about.version</name><val xsi:type="xsd:string" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">rp-version-unique-777</val></propSet>
                                <propSet><name>content.about.fullName</name><val xsi:type="xsd:string" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">rp-fullname-fake-lab</val></propSet>
                              </returnval>
                            </RetrievePropertiesResponse>
                            """, null);
                }
            } else if (request.contains("<vim:Logout")) {
                logoutCount.incrementAndGet();
                respond(exchange, 200, "<LogoutResponse xmlns=\"urn:vim25\"/>", null);
            } else {
                respond(exchange, 500, "<soapenv:Fault><faultstring>unexpected request</faultstring></soapenv:Fault>", null);
            }
        });
        server.start();
        hostWithPort = "127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
                                String bodyFragment, String setCookie) throws IOException {
        String body = bodyFragment.contains("soapenv:Fault") ? wrapFault(bodyFragment) : wrapBody(bodyFragment);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        if (setCookie != null) {
            exchange.getResponseHeaders().add("Set-Cookie", setCookie);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String wrapBody(String fragment) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                %s
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(fragment);
    }

    private static String wrapFault(String faultFragment) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                %s
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(faultFragment);
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
                    "test-vmware",
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
