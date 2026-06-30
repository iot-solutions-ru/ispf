package com.ispf.driver.cwmp;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwmpDeviceDriverTest {

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private HttpServer acsServer;
    private String acsUrl;
    private final AtomicInteger setParameterResponseCount = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (acsServer != null) {
            acsServer.stop(0);
            acsServer = null;
        }
    }

    @Test
    void writePointPostsSetParameterValuesResponseAndUpdatesVariable() throws Exception {
        startMockAcs();
        StubDriverObject driverObject = driverConfig();
        CwmpDeviceDriver driver = new CwmpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("softwareVersion", "Device.DeviceInfo.SoftwareVersion"));

        driver.writePoint("softwareVersion", DataRecord.single(STRING_SCHEMA, Map.of("value", "3.1.0")));

        assertEquals("3.1.0", driverObject.variables.get("softwareVersion").firstRow().get("value"));
        assertEquals(1, setParameterResponseCount.get());
        driver.disconnect();
    }

    @Test
    void writePointRejectsUnknownAndConnectedPoints() throws Exception {
        startMockAcs();
        StubDriverObject driverObject = driverConfig();
        CwmpDeviceDriver driver = new CwmpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "softwareVersion", "Device.DeviceInfo.SoftwareVersion",
                "cwmpConnected", "connected"
        ));

        assertThrows(DriverException.class, () ->
                driver.writePoint("missing", DataRecord.single(STRING_SCHEMA, Map.of("value", "1"))));
        assertThrows(DriverException.class, () ->
                driver.writePoint("cwmpConnected", DataRecord.single(STRING_SCHEMA, Map.of("value", "true"))));
        driver.disconnect();
    }

    private void startMockAcs() throws IOException {
        setParameterResponseCount.set(0);
        acsServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        acsServer.createContext("/", this::handleAcsRequest);
        acsServer.start();
        acsUrl = "http://127.0.0.1:" + acsServer.getAddress().getPort() + "/";
    }

    private void handleAcsRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response;
        if (body.contains("cwmp:Inform")) {
            response = """
                    <?xml version="1.0"?>
                    <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                      <soap-env:Body>
                        <cwmp:GetParameterValues xmlns:cwmp="urn:dslforum-org:cwmp-1-0">
                          <ParameterNames soap-env:arrayType="xsd:string[1]">
                            <string>Device.DeviceInfo.SoftwareVersion</string>
                          </ParameterNames>
                        </cwmp:GetParameterValues>
                      </soap-env:Body>
                    </soap-env:Envelope>
                    """;
        } else if (body.contains("GetParameterValuesResponse")) {
            response = """
                    <?xml version="1.0"?>
                    <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                      <soap-env:Body>
                        <ParameterList>
                          <Name>Device.DeviceInfo.SoftwareVersion</Name>
                          <Value>2.0.0</Value>
                        </ParameterList>
                      </soap-env:Body>
                    </soap-env:Envelope>
                    """;
        } else if (body.contains("SetParameterValuesResponse")) {
            setParameterResponseCount.incrementAndGet();
            response = "<ok/>";
        } else {
            response = "<ok/>";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private StubDriverObject driverConfig() {
        return new StubDriverObject(Map.of(
                "acsUrl", acsUrl,
                "deviceId", "TEST-CPE-001",
                "timeoutMs", "5000",
                "informParameters", "Device.DeviceInfo.SoftwareVersion"
        ));
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
                    "test-cpe",
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
