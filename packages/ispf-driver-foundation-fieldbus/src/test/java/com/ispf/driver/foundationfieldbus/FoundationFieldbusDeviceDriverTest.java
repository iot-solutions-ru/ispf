package com.ispf.driver.foundationfieldbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.foundationfieldbus.codec.FoundationFieldbusCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationFieldbusDeviceDriverTest {

    private FoundationFieldbusDeviceDriver driver;
    private FakeFfPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (peer != null) { peer.close(); peer = null; }
    }

    @Test
    void metadataDescribesHseOverTcp() {
        driver = new FoundationFieldbusDeviceDriver();
        assertEquals("foundation-fieldbus", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("hse"));
        assertTrue(description.contains("tcp"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsAiAoDeviceFf() throws Exception {
        assertEquals(1, FoundationFieldbusPoint.parse("ai:1").index());
        assertEquals(2, FoundationFieldbusPoint.parse("ao:2").index());
        assertEquals(0, FoundationFieldbusPoint.parse("device:0:pv").index());
        assertEquals(1, FoundationFieldbusPoint.parse("ff:1").index());
    }

    @Test
    void readAndWriteViaFdaProbe() throws Exception {
        peer = new FakeFfPeer();
        peer.put(1, 1, 12.5f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new FoundationFieldbusDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("ai", "ai:1"));
        assertEquals(12.5, (Double) object.variables.get("ai").firstRow().get("value"), 0.001);

        driver.writePoint("ai", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 7.25)
        ));
        assertEquals(7.25f, peer.get(1, 1), 0.001f);
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new FoundationFieldbusDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("x", "ai:1")));
    }

    private static final class FakeFfPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-ff");
            t.setDaemon(true);
            return t;
        });
        private final Map<Long, Float> values = new ConcurrentHashMap<>();

        FakeFfPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() { return serverSocket.getLocalPort(); }
        void put(int kind, int index, float value) { values.put(key(kind, index), value); }
        float get(int kind, int index) { return values.getOrDefault(key(kind, index), 0f); }
        void start() { executor.submit(this::acceptLoop); }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    byte[] probe = FoundationFieldbusCodec.readFully(in, 4);
                    int length = ((probe[2] & 0xFF) << 8) | (probe[3] & 0xFF);
                    byte[] body = FoundationFieldbusCodec.readFully(in, length);
                    int op = body[0] & 0xFF;
                    int kind = body[1] & 0xFF;
                    int index = ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
                    if (op == 0x01) {
                        out.write(FoundationFieldbusCodec.encodeFloat(
                                values.getOrDefault(key(kind, index), 0f)));
                        out.flush();
                    } else {
                        float value = ByteBuffer.wrap(body, 4, 4).getFloat();
                        values.put(key(kind, index), value);
                        out.write(0x00);
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private static long key(int kind, int index) {
            return (((long) kind) << 16) | (index & 0xFFFF);
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
            return new PlatformObject("t", "root.platform.devices.t", ObjectType.DEVICE, "T", "", null);
        }
        @Override public void updateVariable(String name, DataRecord value) { variables.put(name, value); }
        @Override public Optional<DataRecord> getVariable(String name) { return Optional.ofNullable(variables.get(name)); }
        @Override public void log(DeviceDriver.DriverLogLevel level, String message) { }
        @Override public Map<String, String> configuration() { return configuration; }
    }
}
