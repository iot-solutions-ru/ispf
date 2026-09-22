package com.ispf.driver.interbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.interbus.codec.InterbusCodec;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterbusDeviceDriverTest {

    private InterbusDeviceDriver driver;
    private FakeInterbusPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (peer != null) { peer.close(); peer = null; }
    }

    @Test
    void metadataDescribesProcessImageOverTcp() {
        driver = new InterbusDeviceDriver();
        assertEquals("interbus", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("process image"));
        assertTrue(description.contains("tcp"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsSlotWordForms() throws Exception {
        assertEquals(1, InterbusPoint.parse("slot:1").slot());
        assertEquals(0, InterbusPoint.parse("word:0").word());
        InterbusPoint both = InterbusPoint.parse("slot:1:word:0");
        assertEquals(1, both.slot());
        assertEquals(0, both.word());
    }

    @Test
    void readAndWriteProcessImage() throws Exception {
        peer = new FakeInterbusPeer();
        peer.put(1, 0, 0x1234);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new InterbusDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("w", "slot:1:word:0"));
        assertEquals(0x1234, ((Double) object.variables.get("w").firstRow().get("value")).intValue());

        driver.writePoint("w", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 0xABCD)
        ));
        assertEquals(0xABCD, peer.get(1, 0));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new InterbusDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("x", "slot:1")));
    }

    private static final class FakeInterbusPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-interbus");
            t.setDaemon(true);
            return t;
        });
        private final Map<Long, Integer> values = new ConcurrentHashMap<>();

        FakeInterbusPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() { return serverSocket.getLocalPort(); }
        void put(int slot, int word, int value) { values.put(key(slot, word), value); }
        int get(int slot, int word) { return values.getOrDefault(key(slot, word), 0); }
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
                    byte[] lenBytes = InterbusCodec.readFully(in, 2);
                    int length = ((lenBytes[0] & 0xFF) << 8) | (lenBytes[1] & 0xFF);
                    byte[] payload = InterbusCodec.readFully(in, length);
                    int slot = payload[0] & 0xFF;
                    int word = payload[1] & 0xFF;
                    if (length == 2) {
                        out.write(InterbusCodec.encodeProcessImage(values.getOrDefault(key(slot, word), 0)));
                        out.flush();
                    } else if (length >= 4) {
                        int value = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
                        values.put(key(slot, word), value);
                        out.write(new byte[] { 0x00, 0x00 });
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private static long key(int slot, int word) {
            return (((long) slot) << 8) | (word & 0xFF);
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
