package com.ispf.driver.websocket;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loopback tests for {@link WebsocketDeviceDriver} against an in-process fake RFC6455 server. */
class WebsocketDeviceDriverTest {

    private WebsocketDeviceDriver driver;
    private FakeWebSocketServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (server != null) { server.close(); server = null; }
    }

    @Test
    void getAndSetViaMessageKeyAndChannelPath() throws Exception {
        server = new FakeWebSocketServer();
        server.put("temperature", "23.5");
        server.put("/sensors/pressure", "101.3");
        server.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "path", "/ws",
                "timeoutMs", "2000"
        ));
        driver = new WebsocketDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of("temp", "temperature", "pressure", "/sensors/pressure"));
        assertEquals("23.5", object.variables.get("temp").firstRow().get("value"));
        assertEquals("temperature", object.variables.get("temp").firstRow().get("point"));
        assertEquals("101.3", object.variables.get("pressure").firstRow().get("value"));
        assertEquals("/sensors/pressure", object.variables.get("pressure").firstRow().get("point"));
        driver.writePoint("temp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")));
        assertEquals("24.1", server.get("temperature"));
        assertEquals("24.1", object.variables.get("temp").firstRow().get("value"));
        driver.readPoints(Map.of("temp", "temperature"));
        assertEquals("24.1", object.variables.get("temp").firstRow().get("value"));
    }

    @Test
    void acceptKeyMatchesRfc6455Example() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                Rfc6455Client.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new WebsocketDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("k", "temperature")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void connectFailsAgainstClosedPort() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1", "port", String.valueOf(closedPort), "timeoutMs", "200"));
        driver = new WebsocketDeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("WebSocket connect failed"));
    }

    private static final class FakeWebSocketServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ws-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> store = new ConcurrentHashMap<>();
        FakeWebSocketServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }
        int port() { return serverSocket.getLocalPort(); }
        void put(String key, String value) { store.put(key, value); }
        String get(String key) { return store.get(key); }
        void start() { executor.submit(this::acceptLoop); }
        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) { return; }
                }
            }
        }
        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                Rfc6455Client.ServerHelpers.completeHandshake(in, out, new HashMap<>());
                while (true) {
                    String text = Rfc6455Client.ServerHelpers.readMaskedText(in);
                    String op = Rfc6455Client.extractJsonField(text, "op");
                    String point = Rfc6455Client.extractJsonField(text, "point");
                    if ("set".equals(op)) {
                        String value = Rfc6455Client.extractJsonField(text, "value");
                        store.put(point == null ? "" : point, value == null ? "" : value);
                        Rfc6455Client.ServerHelpers.writeUnmaskedText(out,
                                "{\"ok\":true,\"point\":\"" + Rfc6455Client.escapeJson(point)
                                        + "\",\"value\":\"" + Rfc6455Client.escapeJson(value) + "\"}");
                    } else {
                        String value = store.getOrDefault(point, "");
                        Rfc6455Client.ServerHelpers.writeUnmaskedText(out,
                                "{\"point\":\"" + Rfc6455Client.escapeJson(point)
                                        + "\",\"value\":\"" + Rfc6455Client.escapeJson(value) + "\"}");
                    }
                }
            } catch (IOException ignored) { }
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
        StubDriverObject(Map<String, String> configuration) { this.configuration = configuration; }
        @Override public PlatformObject deviceObject() {
            return new PlatformObject("test-websocket", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
        }
        @Override public void updateVariable(String name, DataRecord value) { variables.put(name, value); }
        @Override public Optional<DataRecord> getVariable(String name) { return Optional.ofNullable(variables.get(name)); }
        @Override public void log(DeviceDriver.DriverLogLevel level, String message) { }
        @Override public Map<String, String> configuration() { return configuration; }
    }
}
