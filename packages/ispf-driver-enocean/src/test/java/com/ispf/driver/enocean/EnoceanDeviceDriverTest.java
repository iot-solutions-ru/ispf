package com.ispf.driver.enocean;

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

class EnoceanDeviceDriverTest {

    private EnoceanDeviceDriver driver;
    private FakeGateway gateway;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (gateway != null) { gateway.close(); gateway = null; }
    }

    @Test
    void metadataIsProductionReadWrite() {
        driver = new EnoceanDeviceDriver();
        assertEquals("enocean", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void getAndTxLoopback() throws Exception {
        gateway = new FakeGateway();
        gateway.put("AABBCCDD", "01FF");
        gateway.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new EnoceanDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of("sw", "AABBCCDD"));
        assertEquals("01FF", object.variables.get("sw").firstRow().get("value"));
        driver.writePoint("sw", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "00AA")
        ));
        assertEquals("00AA", gateway.get("AABBCCDD"));
    }

    private static final class FakeGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "fake-enocean"); t.setDaemon(true); return t; });
        private final Map<String, String> payloads = new ConcurrentHashMap<>();
        FakeGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }
        int port() { return serverSocket.getLocalPort(); }
        void put(String id, String data) { payloads.put(id.toUpperCase(Locale.ROOT), data.toUpperCase(Locale.ROOT)); }
        String get(String id) { return payloads.get(id.toUpperCase(Locale.ROOT)); }
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
                    String cmd = EnoceanDeviceDriver.readLine(in);
                    if (cmd == null) return;
                    String upper = cmd.trim().toUpperCase(Locale.ROOT);
                    if (upper.startsWith("GET ")) {
                        String id = upper.substring(4).trim();
                        EnoceanDeviceDriver.writeLine(out, "RX " + id + " " + payloads.getOrDefault(id, ""));
                    } else if (upper.startsWith("TX ")) {
                        String[] p = upper.substring(3).trim().split("\\s+");
                        payloads.put(p[0], p.length > 1 ? p[1] : "");
                        EnoceanDeviceDriver.writeLine(out, "OK " + p[0] + " " + payloads.get(p[0]));
                    } else {
                        EnoceanDeviceDriver.writeLine(out, "ERR");
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
            return new PlatformObject("test-enocean", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
        }
        @Override public void updateVariable(String name, DataRecord value) { variables.put(name, value); }
        @Override public Optional<DataRecord> getVariable(String name) { return Optional.ofNullable(variables.get(name)); }
        @Override public void log(DeviceDriver.DriverLogLevel level, String message) {}
        @Override public Map<String, String> configuration() { return configuration; }
    }
}
