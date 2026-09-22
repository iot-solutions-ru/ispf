package com.ispf.driver.mtconnect;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link MtconnectDeviceDriver} against an in-process fake MTConnect Agent.
 */
class MtconnectDeviceDriverTest {

    private MtconnectDeviceDriver driver;
    private FakeMtconnectAgent agent;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (agent != null) {
            agent.close();
            agent = null;
        }
    }

    @Test
    void httpGetRequestStartsWithGetAndHttp11() {
        String request = MtconnectDeviceDriver.buildGetRequest("/current", "127.0.0.1:5000");
        assertTrue(request.startsWith("GET "));
        assertTrue(request.contains("HTTP/1.1"));
        assertTrue(request.startsWith("GET /current HTTP/1.1\r\n"));
    }

    @Test
    void currentStreamsExtractDataItems() throws Exception {
        agent = new FakeMtconnectAgent();
        agent.setCurrentXml(sampleCurrentXml());
        agent.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "baseUrl", agent.baseUrl(),
                "path", "/current",
                "timeoutMs", "2000"
        ));
        driver = new MtconnectDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "xpos", "x_pos",
                "exec", "name:Execution",
                "mode", "id:mode"
        ));

        String captured = agent.lastRawRequest();
        assertTrue(captured.startsWith("GET "));
        assertTrue(captured.contains("HTTP/1.1"));

        assertEquals("125.5", object.variables.get("xpos").firstRow().get("value"));
        assertEquals("x_pos", object.variables.get("xpos").firstRow().get("dataItemId"));
        assertEquals("Xact", object.variables.get("xpos").firstRow().get("name"));
        assertEquals("Samples", object.variables.get("xpos").firstRow().get("category"));

        assertEquals("ACTIVE", object.variables.get("exec").firstRow().get("value"));
        assertEquals("Events", object.variables.get("exec").firstRow().get("category"));

        assertEquals("AUTOMATIC", object.variables.get("mode").firstRow().get("value"));
    }

    @Test
    void samplePathAndDeviceFilter() throws Exception {
        agent = new FakeMtconnectAgent();
        agent.setSampleXml(sampleCurrentXml());
        agent.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "baseUrl", agent.baseUrl(),
                "path", "/sample",
                "device", "Mill-1",
                "timeoutMs", "2000"
        ));
        driver = new MtconnectDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("xpos", "Xact"));
        assertEquals("125.5", object.variables.get("xpos").firstRow().get("value"));
        assertEquals("/sample", agent.lastPath());
    }

    @Test
    void missingDataItemReturnsEmptyValue() throws Exception {
        agent = new FakeMtconnectAgent();
        agent.setCurrentXml(sampleCurrentXml());
        agent.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "baseUrl", agent.baseUrl()
        ));
        driver = new MtconnectDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("missing", "no_such_item"));
        assertEquals("", object.variables.get("missing").firstRow().get("value"));
    }

    @Test
    void writePointIsUnsupported() throws Exception {
        agent = new FakeMtconnectAgent();
        agent.setCurrentXml(sampleCurrentXml());
        agent.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "baseUrl", agent.baseUrl()
        ));
        driver = new MtconnectDeviceDriver();
        driver.initialize(object);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("xpos", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.STRING).build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("poll-only"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new MtconnectDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "x_pos")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void pointParseForms() {
        assertEquals("name:xact", MtconnectPoint.parse("name:Xact").key());
        assertEquals("id:x_pos", MtconnectPoint.parse("id:x_pos").key());
        assertEquals("xact", MtconnectPoint.parse("Xact").key());
    }

    @Test
    void metadataHasNoLabWording() {
        String desc = new MtconnectDeviceDriver().metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(desc.contains("http/1.1"));
        assertTrue(!desc.contains("lab"));
    }

    private static String sampleCurrentXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <MTConnectStreams xmlns="urn:mtconnect.org:MTConnectStreams:1.8">
                  <Header creationTime="2026-09-05T09:00:00Z" sender="fake-agent" version="1.8.0"
                          bufferSize="128" firstSequence="1" lastSequence="3" nextSequence="4"/>
                  <Streams>
                    <DeviceStream name="Mill-1" uuid="mill-1">
                      <ComponentStream component="Linear" name="X" componentId="x">
                        <Samples>
                          <Position dataItemId="x_pos" name="Xact" sequence="1"
                                    timestamp="2026-09-05T09:00:00Z">125.5</Position>
                        </Samples>
                      </ComponentStream>
                      <ComponentStream component="Controller" name="Controller" componentId="cont">
                        <Events>
                          <Execution dataItemId="exec" name="Execution" sequence="2"
                                     timestamp="2026-09-05T09:00:00Z">ACTIVE</Execution>
                          <ControllerMode dataItemId="mode" name="Mode" sequence="3"
                                          timestamp="2026-09-05T09:00:00Z">AUTOMATIC</ControllerMode>
                        </Events>
                      </ComponentStream>
                    </DeviceStream>
                    <DeviceStream name="Other" uuid="other">
                      <ComponentStream component="Linear" name="X" componentId="ox">
                        <Samples>
                          <Position dataItemId="other_x" name="Xact" sequence="9"
                                    timestamp="2026-09-05T09:00:00Z">999</Position>
                        </Samples>
                      </ComponentStream>
                    </DeviceStream>
                  </Streams>
                </MTConnectStreams>
                """;
    }

    private static final class FakeMtconnectAgent implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-mtconnect-agent");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicReference<String> currentXml = new AtomicReference<>("");
        private final AtomicReference<String> sampleXml = new AtomicReference<>("");
        private final AtomicReference<String> lastPath = new AtomicReference<>("");
        private final AtomicReference<String> lastRawRequest = new AtomicReference<>("");

        FakeMtconnectAgent() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        void setCurrentXml(String xml) {
            currentXml.set(xml);
            sampleXml.set(xml);
        }

        void setSampleXml(String xml) {
            sampleXml.set(xml);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        String lastPath() {
            return lastPath.get();
        }

        String lastRawRequest() {
            return lastRawRequest.get();
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String requestLine = MtconnectDeviceDriver.readLine(in);
                if (requestLine == null) {
                    return;
                }
                StringBuilder raw = new StringBuilder(requestLine).append("\r\n");
                while (true) {
                    String line = MtconnectDeviceDriver.readLine(in);
                    if (line == null) {
                        return;
                    }
                    raw.append(line).append("\r\n");
                    if (line.isEmpty()) {
                        break;
                    }
                }
                lastRawRequest.set(raw.toString());
                String[] parts = requestLine.split("\\s+");
                String path = parts.length > 1 ? parts[1] : "/";
                lastPath.set(path);
                String body;
                if ("/current".equals(path)) {
                    body = currentXml.get();
                } else if ("/sample".equals(path)) {
                    body = sampleXml.get();
                } else {
                    writeResponse(out, 404, "text/plain", "not found");
                    return;
                }
                writeResponse(out, 200, "application/xml; charset=utf-8", body);
            } catch (IOException ignored) {
            }
        }

        private static void writeResponse(OutputStream out, int code, String contentType, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            StringBuilder resp = new StringBuilder();
            resp.append("HTTP/1.1 ").append(code).append(code == 200 ? " OK" : " Error").append("\r\n");
            resp.append("Content-Type: ").append(contentType).append("\r\n");
            resp.append("Content-Length: ").append(bytes.length).append("\r\n");
            resp.append("Connection: close\r\n");
            resp.append("\r\n");
            out.write(resp.toString().getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
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
                    "test-mtconnect",
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
