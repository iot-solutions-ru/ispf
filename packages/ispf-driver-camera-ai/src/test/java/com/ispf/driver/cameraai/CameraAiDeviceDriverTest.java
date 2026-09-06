package com.ispf.driver.cameraai;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraAiDeviceDriverTest {

    private CameraAiDeviceDriver driver;
    private FakeInferenceServer server;

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
    void metadataIsProductionReadWrite() {
        driver = new CameraAiDeviceDriver();
        assertEquals("camera-ai", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void getAndPostLoopback() throws Exception {
        server = new FakeInferenceServer();
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new CameraAiDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("det", "/infer"));
        assertEquals("{\"label\":\"box\",\"score\":0.91}", object.variables.get("det").firstRow().get("value"));
        assertEquals("200", object.variables.get("det").firstRow().get("status"));

        driver.writePoint("det", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "{\"threshold\":0.5}")
        ));
        assertEquals("{\"threshold\":0.5}", server.lastBody());
        assertEquals("POST", server.lastMethod());
    }

    private static final class FakeInferenceServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-camera-ai");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<String> lastMethod = new AtomicReference<>("");
        private final AtomicReference<String> lastBody = new AtomicReference<>("");

        FakeInferenceServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String lastMethod() {
            return lastMethod.get();
        }

        String lastBody() {
            return lastBody.get();
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
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String raw = readRequest(in);
                String[] lines = raw.split("\r\n");
                String[] requestLine = lines[0].split("\\s+");
                lastMethod.set(requestLine[0]);
                int contentLength = 0;
                for (String line : lines) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }
                int headerEnd = raw.indexOf("\r\n\r\n");
                String body = headerEnd >= 0 && raw.length() > headerEnd + 4
                        ? raw.substring(headerEnd + 4)
                        : "";
                if (body.length() < contentLength) {
                    // already included in readRequest for lab sizes
                }
                lastBody.set(body.trim());
                String responseBody = "GET".equals(requestLine[0])
                        ? "{\"label\":\"box\",\"score\":0.91}"
                        : "{\"ok\":true}";
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                String response = "HTTP/1.1 200 OK\r\nContent-Length: " + bytes.length
                        + "\r\nConnection: close\r\n\r\n" + responseBody;
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // closed
            }
        }

        private static String readRequest(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[512];
            // read until headers end or timeout via available/read
            while (true) {
                int n = in.read(tmp);
                if (n < 0) {
                    break;
                }
                buf.write(tmp, 0, n);
                String soFar = buf.toString(StandardCharsets.US_ASCII);
                int headerEnd = soFar.indexOf("\r\n\r\n");
                if (headerEnd >= 0) {
                    int contentLength = 0;
                    for (String line : soFar.substring(0, headerEnd).split("\r\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        }
                    }
                    int bodyStart = headerEnd + 4;
                    while (buf.size() < bodyStart + contentLength) {
                        n = in.read(tmp);
                        if (n < 0) {
                            break;
                        }
                        buf.write(tmp, 0, n);
                    }
                    break;
                }
            }
            return buf.toString(StandardCharsets.UTF_8);
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
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-camera-ai", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
