package com.ispf.driver.sigfox;

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

class SigfoxDeviceDriverTest {

    private SigfoxDeviceDriver driver;
    private FakeBackend backend;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (backend != null) { backend.close(); backend = null; }
    }

    @Test
    void metadataIsProductionReadWrite() {
        driver = new SigfoxDeviceDriver();
        assertEquals("sigfox", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void getAndPostLoopback() throws Exception {
        backend = new FakeBackend();
        backend.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(backend.port()),
                "timeoutMs", "2000"
        ));
        driver = new SigfoxDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of("dev", "DEVICE1"));
        assertEquals("{\"data\":\"ABCD\"}", object.variables.get("dev").firstRow().get("value"));
        assertEquals("200", object.variables.get("dev").firstRow().get("status"));
        driver.writePoint("dev", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "{\"downlink\":\"01\"}")
        ));
        assertEquals("POST", backend.lastMethod());
        assertTrue(backend.lastBody().contains("downlink"));
    }

    private static final class FakeBackend implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "fake-sigfox"); t.setDaemon(true); return t; });
        private final AtomicReference<String> lastMethod = new AtomicReference<>("");
        private final AtomicReference<String> lastBody = new AtomicReference<>("");
        FakeBackend() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }
        int port() { return serverSocket.getLocalPort(); }
        String lastMethod() { return lastMethod.get(); }
        String lastBody() { return lastBody.get(); }
        void start() { executor.submit(this::acceptLoop); }
        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try { Socket s = serverSocket.accept(); executor.submit(() -> handle(s)); }
                catch (IOException e) { return; }
            }
        }
        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String raw = readRequest(in);
                String[] lines = raw.split("\r\n");
                String[] rl = lines[0].split("\\s+");
                lastMethod.set(rl[0]);
                int contentLength = 0;
                for (String line : lines) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }
                int he = raw.indexOf("\r\n\r\n");
                String body = he >= 0 ? raw.substring(he + 4) : "";
                lastBody.set(body.trim());
                String responseBody = "GET".equals(rl[0]) ? "{\"data\":\"ABCD\"}" : "{\"ok\":true}";
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                String resp = "HTTP/1.1 200 OK\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n" + responseBody;
                out.write(resp.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {}
        }
        private static String readRequest(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[512];
            while (true) {
                int n = in.read(tmp);
                if (n < 0) break;
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
                        if (n < 0) break;
                        buf.write(tmp, 0, n);
                    }
                    break;
                }
            }
            return buf.toString(StandardCharsets.UTF_8);
        }
        @Override public void close() throws Exception {
            serverSocket.close(); executor.shutdownNow(); executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();
        StubDriverObject(Map<String, String> configuration) { this.configuration = configuration; }
        @Override public PlatformObject deviceObject() {
            return new PlatformObject("test-sigfox", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
        }
        @Override public void updateVariable(String name, DataRecord value) { variables.put(name, value); }
        @Override public Optional<DataRecord> getVariable(String name) { return Optional.ofNullable(variables.get(name)); }
        @Override public void log(DeviceDriver.DriverLogLevel level, String message) {}
        @Override public Map<String, String> configuration() { return configuration; }
    }
}
