package com.ispf.driver.rtsp;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link RtspDeviceDriver} against an in-process fake RTSP/1.0 server.
 */
class RtspDeviceDriverTest {

    private RtspDeviceDriver driver;
    private FakeRtspServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void optionsOnConnectDescribeReadAndSetParameterWrite() throws Exception {
        server = new FakeRtspServer();
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000",
                "streamPath", "/stream"
        ));
        driver = new RtspDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals(1, server.optionsCount.get());

        driver.readPoints(Map.of("sdp", "DESCRIBE"));
        DataRecord sdp = object.variables.get("sdp");
        assertTrue(String.valueOf(sdp.firstRow().get("status")).contains("200"));
        assertTrue(String.valueOf(sdp.firstRow().get("body")).contains("m=video"));
        assertEquals("DESCRIBE", sdp.firstRow().get("method"));
        assertEquals("/stream", sdp.firstRow().get("path"));

        driver.writePoint("sdp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "barlevel=5")
        ));
        assertEquals("barlevel=5", server.lastSetParameterBody.get());
        assertTrue(String.valueOf(object.variables.get("sdp").firstRow().get("status")).contains("200"));
        assertEquals("SET_PARAMETER", object.variables.get("sdp").firstRow().get("method"));
    }

    @Test
    void teardownWriteUsesTeardownMapping() throws Exception {
        server = new FakeRtspServer();
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port())
        ));
        driver = new RtspDeviceDriver();
        driver.initialize(object);
        driver.connect();

        // When no prior readPoints mapping exists, pointId itself is the write mapping.
        driver.writePoint("TEARDOWN /cam1", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "ignored")
        ));
        assertEquals("TEARDOWN", server.lastMethod.get());
        assertTrue(server.lastUri.get().contains("/cam1"));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new RtspDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "OPTIONS")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void metadataIdIsRtsp() {
        assertEquals("rtsp", new RtspDeviceDriver().metadata().id());
        assertTrue(new RtspDeviceDriver().metadata().supportsWrite());
        assertTrue(new RtspDeviceDriver().metadata().description().toLowerCase(Locale.ROOT)
                .contains("not rtp"));
    }

    private static final class FakeRtspServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-rtsp");
            thread.setDaemon(true);
            return thread;
        });
        final AtomicInteger optionsCount = new AtomicInteger();
        final AtomicReference<String> lastMethod = new AtomicReference<>("");
        final AtomicReference<String> lastUri = new AtomicReference<>("");
        final AtomicReference<String> lastSetParameterBody = new AtomicReference<>("");

        FakeRtspServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
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
                while (true) {
                    String requestLine = RtspDeviceDriver.readLine(in);
                    if (requestLine == null) {
                        return;
                    }
                    Map<String, String> headers = new ConcurrentHashMap<>();
                    while (true) {
                        String line = RtspDeviceDriver.readLine(in);
                        if (line == null) {
                            return;
                        }
                        if (line.isEmpty()) {
                            break;
                        }
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                                    line.substring(colon + 1).trim());
                        }
                    }
                    int contentLength = 0;
                    String cl = headers.get("content-length");
                    if (cl != null) {
                        contentLength = Integer.parseInt(cl.trim());
                    }
                    String body = "";
                    if (contentLength > 0) {
                        body = new String(in.readNBytes(contentLength), StandardCharsets.UTF_8);
                    }
                    String[] parts = requestLine.split("\\s+");
                    String method = parts.length > 0 ? parts[0].toUpperCase(Locale.ROOT) : "";
                    String uri = parts.length > 1 ? parts[1] : "";
                    lastMethod.set(method);
                    lastUri.set(uri);
                    String cseq = headers.getOrDefault("cseq", "1");

                    if ("OPTIONS".equals(method)) {
                        optionsCount.incrementAndGet();
                        writeResponse(out, cseq, 200, "Public: OPTIONS, DESCRIBE, SET_PARAMETER, TEARDOWN\r\n", "");
                    } else if ("DESCRIBE".equals(method)) {
                        String sdp = "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=Lab\r\nm=video 0 RTP/AVP 96\r\n";
                        writeResponse(out, cseq, 200,
                                "Content-Type: application/sdp\r\nContent-Length: "
                                        + sdp.getBytes(StandardCharsets.UTF_8).length + "\r\n",
                                sdp);
                    } else if ("SET_PARAMETER".equals(method)) {
                        lastSetParameterBody.set(body);
                        writeResponse(out, cseq, 200, "", "");
                    } else if ("TEARDOWN".equals(method)) {
                        writeResponse(out, cseq, 200, "", "");
                    } else {
                        writeResponse(out, cseq, 501, "", "");
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private static void writeResponse(OutputStream out, String cseq, int code, String extraHeaders, String body)
                throws IOException {
            String reason = code == 200 ? "OK" : "Error";
            StringBuilder resp = new StringBuilder();
            resp.append("RTSP/1.0 ").append(code).append(' ').append(reason).append("\r\n");
            resp.append("CSeq: ").append(cseq).append("\r\n");
            resp.append(extraHeaders);
            if (!extraHeaders.toLowerCase(Locale.ROOT).contains("content-length")) {
                resp.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
            }
            resp.append("\r\n");
            resp.append(body);
            out.write(resp.toString().getBytes(StandardCharsets.UTF_8));
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
                    "test-rtsp",
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
