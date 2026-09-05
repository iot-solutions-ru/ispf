package com.ispf.driver.genicam;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenicamDeviceDriverTest {

    private GenicamDeviceDriver driver;
    private FakeCamera camera;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (camera != null) { camera.close(); camera = null; }
    }

    @Test
    void metadataIsProductionReadWrite() {
        driver = new GenicamDeviceDriver();
        assertEquals("genicam", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void getAndSetLoopback() throws Exception {
        camera = new FakeCamera();
        camera.put("Width", "1920");
        camera.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(camera.port()),
                "timeoutMs", "2000"
        ));
        driver = new GenicamDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of("w", "Width"));
        assertEquals("1920", object.variables.get("w").firstRow().get("value"));
        driver.writePoint("w", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "1280")
        ));
        assertEquals("1280", camera.get("Width"));
    }

    private static final class FakeCamera implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "fake-genicam"); t.setDaemon(true); return t; });
        private final Map<String, String> features = new ConcurrentHashMap<>();
        FakeCamera() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }
        int port() { return serverSocket.getLocalPort(); }
        void put(String f, String v) { features.put(f, v); }
        String get(String f) { return features.get(f); }
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
                while (true) {
                    String cmd = GenicamDeviceDriver.readLine(in);
                    if (cmd == null) return;
                    String upper = cmd.trim().toUpperCase(Locale.ROOT);
                    if (upper.startsWith("GET ")) {
                        String feature = cmd.trim().substring(4).trim();
                        GenicamDeviceDriver.writeLine(out, "VALUE " + features.getOrDefault(feature, ""));
                    } else if (upper.startsWith("SET ")) {
                        String[] parts = cmd.trim().substring(4).trim().split("\\s+", 2);
                        features.put(parts[0], parts.length > 1 ? parts[1] : "");
                        GenicamDeviceDriver.writeLine(out, "OK " + features.get(parts[0]));
                    } else {
                        GenicamDeviceDriver.writeLine(out, "ERR");
                    }
                }
            } catch (IOException ignored) {}
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
            return new PlatformObject("test-genicam", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
        }
        @Override public void updateVariable(String name, DataRecord value) { variables.put(name, value); }
        @Override public Optional<DataRecord> getVariable(String name) { return Optional.ofNullable(variables.get(name)); }
        @Override public void log(DeviceDriver.DriverLogLevel level, String message) {}
        @Override public Map<String, String> configuration() { return configuration; }
    }
}
