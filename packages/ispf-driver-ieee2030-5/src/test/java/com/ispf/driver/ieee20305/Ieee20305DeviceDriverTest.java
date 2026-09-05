package com.ispf.driver.ieee20305;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link Ieee20305DeviceDriver} against a fake IEEE 2030.5 HTTP/XML server.
 */
class Ieee20305DeviceDriverTest {

    private Ieee20305DeviceDriver driver;
    private HttpServer sepServer;
    private String baseUrl;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (sepServer != null) {
            sepServer.stop(0);
            sepServer = null;
        }
    }

    @Test
    void metadataDescribesSep2SubsetNotStub() {
        driver = new Ieee20305DeviceDriver();
        assertEquals("ieee2030-5", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().contains("2030.5"));
        assertTrue(driver.metadata().description().toLowerCase().contains("enddevicelist")
                || driver.metadata().description().contains("MeterReading"));
    }

    @Test
    void getsEndDeviceListAndMeterReadingViaLoopback() throws Exception {
        startFakeSep2Server();

        StubDriverObject object = new StubDriverObject(Map.of(
                "baseUrl", baseUrl,
                "timeoutMs", "3000"
        ));
        driver = new Ieee20305DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "sfdi", "/edev",
                "reading", "/upt/1/mr/1/r:value"
        ));

        DataRecord sfdi = object.variables.get("sfdi");
        assertEquals("1234567890", sfdi.firstRow().get("value"));
        assertEquals("/edev", sfdi.firstRow().get("path"));
        assertEquals("sFDI", sfdi.firstRow().get("field"));
        assertEquals(200, ((Number) sfdi.firstRow().get("statusCode")).intValue());

        DataRecord reading = object.variables.get("reading");
        assertEquals("98765", reading.firstRow().get("value"));
        assertEquals("/upt/1/mr/1/r", reading.firstRow().get("path"));
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(new Ieee20305Point("/edev", "sFDI"), Ieee20305Point.parse("/edev"));
        assertEquals(new Ieee20305Point("/edev", "lFDI"), Ieee20305Point.parse("/edev:lFDI"));
        assertEquals(new Ieee20305Point("/upt/1/mr/1/r", "value"), Ieee20305Point.parse("/upt/1/mr/1/r:value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new Ieee20305DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("baseUrl", "http://127.0.0.1:9")));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("sfdi", "/edev")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writePointIsGetOnly() throws Exception {
        startFakeSep2Server();
        StubDriverObject object = new StubDriverObject(Map.of("baseUrl", baseUrl));
        driver = new Ieee20305DeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("sfdi", "/edev"));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("sfdi", object.variables.get("sfdi")));
        assertTrue(error.getMessage().toLowerCase().contains("get-only"));
    }

    private void startFakeSep2Server() throws IOException {
        sepServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sepServer.createContext("/edev", exchange -> respond(exchange, """
                <?xml version="1.0" encoding="UTF-8"?>
                <EndDeviceList xmlns="urn:ieee:std:2030.5:ns" href="/edev" all="1" results="1">
                  <EndDevice href="/edev/1">
                    <lFDI>00112233445566778899AABBCCDDEEFF00112233</lFDI>
                    <sFDI>1234567890</sFDI>
                    <changedTime>0</changedTime>
                  </EndDevice>
                </EndDeviceList>
                """));
        sepServer.createContext("/upt/1/mr/1/r", exchange -> respond(exchange, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Reading xmlns="urn:ieee:std:2030.5:ns" href="/upt/1/mr/1/r">
                  <value>98765</value>
                  <timePeriod>
                    <duration>0</duration>
                    <start>0</start>
                  </timePeriod>
                </Reading>
                """));
        sepServer.start();
        baseUrl = "http://127.0.0.1:" + sepServer.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String xml) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/sep+xml; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
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
                    "test-ieee2030-5",
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
