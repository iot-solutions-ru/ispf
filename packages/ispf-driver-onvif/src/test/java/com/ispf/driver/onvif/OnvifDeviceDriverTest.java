package com.ispf.driver.onvif;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link OnvifDeviceDriver} against a fake ONVIF Device SOAP server.
 */
class OnvifDeviceDriverTest {

    private OnvifDeviceDriver driver;
    private HttpServer server;
    private final AtomicReference<String> hostname = new AtomicReference<>("cam-lab");

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
    void readsDeviceInformationAndCapabilities() throws Exception {
        startServer();
        StubDriverObject object = new StubDriverObject(Map.of(
                "deviceServiceUrl", serviceUrl(),
                "timeoutMs", "2000"
        ));
        driver = new OnvifDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "mfr", "Manufacturer",
                "model", "Model",
                "fw", "FirmwareVersion",
                "device", "DeviceXAddr",
                "host", "Hostname"
        ));

        assertEquals("ISPF Labs", object.variables.get("mfr").firstRow().get("value"));
        assertEquals("LabCam", object.variables.get("model").firstRow().get("value"));
        assertEquals("1.2.3", object.variables.get("fw").firstRow().get("value"));
        assertTrue(String.valueOf(object.variables.get("device").firstRow().get("value")).contains("/onvif/device_service"));
        assertEquals("cam-lab", object.variables.get("host").firstRow().get("value"));
    }

    @Test
    void writesHostnameViaSetHostname() throws Exception {
        startServer();
        StubDriverObject object = new StubDriverObject(Map.of(
                "deviceServiceUrl", serviceUrl()
        ));
        driver = new OnvifDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("host", "Hostname"));

        driver.writePoint("host", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "plant-cam-01")
        ));
        assertEquals("plant-cam-01", hostname.get());
        assertEquals("plant-cam-01", object.variables.get("host").firstRow().get("value"));

        driver.readPoints(Map.of("host", "Hostname"));
        assertEquals("plant-cam-01", object.variables.get("host").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new OnvifDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("m", "Manufacturer")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void metadataIdIsOnvif() {
        assertEquals("onvif", new OnvifDeviceDriver().metadata().id());
        assertTrue(new OnvifDeviceDriver().metadata().supportsWrite());
    }

    private void startServer() throws IOException {
        hostname.set("cam-lab");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/onvif/device_service", this::handle);
        server.start();
    }

    private String serviceUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/onvif/device_service";
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response;
        if (body.contains("GetDeviceInformation")) {
            response = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                                xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
                                xmlns:tt="http://www.onvif.org/ver10/schema">
                      <s:Body>
                        <tds:GetDeviceInformationResponse>
                          <tds:Manufacturer>ISPF Labs</tds:Manufacturer>
                          <tds:Model>LabCam</tds:Model>
                          <tds:FirmwareVersion>1.2.3</tds:FirmwareVersion>
                          <tds:SerialNumber>SN-001</tds:SerialNumber>
                          <tds:HardwareId>HW-9</tds:HardwareId>
                        </tds:GetDeviceInformationResponse>
                      </s:Body>
                    </s:Envelope>
                    """;
        } else if (body.contains("GetCapabilities")) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            response = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                                xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
                                xmlns:tt="http://www.onvif.org/ver10/schema">
                      <s:Body>
                        <tds:GetCapabilitiesResponse>
                          <tds:Capabilities>
                            <tt:Device>
                              <tt:XAddr>%s/onvif/device_service</tt:XAddr>
                            </tt:Device>
                            <tt:Media>
                              <tt:XAddr>%s/onvif/media_service</tt:XAddr>
                            </tt:Media>
                          </tds:Capabilities>
                        </tds:GetCapabilitiesResponse>
                      </s:Body>
                    </s:Envelope>
                    """.formatted(base, base);
        } else if (body.contains("GetHostname")) {
            response = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                                xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
                      <s:Body>
                        <tds:GetHostnameResponse>
                          <tds:HostnameInformation>
                            <tds:FromDHCP>false</tds:FromDHCP>
                            <tds:Name>%s</tds:Name>
                          </tds:HostnameInformation>
                        </tds:GetHostnameResponse>
                      </s:Body>
                    </s:Envelope>
                    """.formatted(hostname.get());
        } else if (body.contains("SetHostname")) {
            int start = body.indexOf("<tds:Name>");
            int end = body.indexOf("</tds:Name>");
            if (start >= 0 && end > start) {
                hostname.set(body.substring(start + "<tds:Name>".length(), end));
            }
            response = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                                xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
                      <s:Body>
                        <tds:SetHostnameResponse/>
                      </s:Body>
                    </s:Envelope>
                    """;
        } else {
            response = "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body/></s:Envelope>";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/soap+xml");
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
                    "test-onvif",
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
