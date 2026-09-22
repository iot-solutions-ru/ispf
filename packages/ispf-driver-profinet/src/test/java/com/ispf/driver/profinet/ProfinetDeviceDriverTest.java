package com.ispf.driver.profinet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.profinet.codec.ProfinetCodec;
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

class ProfinetDeviceDriverTest {

    private ProfinetDeviceDriver driver;
    private FakeProfinetPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (peer != null) {
            peer.close();
            peer = null;
        }
    }

    @Test
    void metadataDescribesDcpNotRtIrt() {
        driver = new ProfinetDeviceDriver();
        assertEquals("profinet", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("dcp"));
        assertTrue(description.contains("not"));
        assertFalse(description.contains("lab"));
        assertFalse(description.contains("stub"));
        assertFalse(description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsSlotSubslotAndDeviceApiForms() throws Exception {
        ProfinetPoint slot = ProfinetPoint.parse("slot:1:subslot:1");
        assertEquals(1, slot.slot());
        assertEquals(1, slot.subslot());

        ProfinetPoint device = ProfinetPoint.parse("device:1:api:0:slot:1");
        assertEquals(1, device.slot());
        assertEquals(0, device.subslot());
    }

    @Test
    void readAndWriteViaDcpIdentify() throws Exception {
        peer = new FakeProfinetPeer();
        peer.put(1, 1, 12.5f);
        peer.put(1, 0, 21.0f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ProfinetDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "io", "slot:1:subslot:1",
                "dev", "device:1:api:0:slot:1"
        ));
        assertEquals(12.5, (Double) object.variables.get("io").firstRow().get("value"), 0.001);
        assertEquals(21.0, (Double) object.variables.get("dev").firstRow().get("value"), 0.001);

        driver.writePoint("io", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.25)
        ));
        assertEquals(33.25f, peer.get(1, 1), 0.001f);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ProfinetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "slot:1:subslot:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeProfinetPeer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-profinet");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Long, Float> values = new ConcurrentHashMap<>();

        FakeProfinetPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int slot, int subslot, float value) {
            values.put(key(slot, subslot), value);
        }

        float get(int slot, int subslot) {
            return values.getOrDefault(key(slot, subslot), 0f);
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
                    byte[] header = ProfinetCodec.readFully(in, 12);
                    int dcpLen = ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
                    byte[] blocks = dcpLen > 0 ? ProfinetCodec.readFully(in, dcpLen) : new byte[0];
                    int slot = 0;
                    int subslot = 0;
                    if (blocks.length >= 8) {
                        slot = ((blocks[4] & 0xFF) << 8) | (blocks[5] & 0xFF);
                        subslot = ((blocks[6] & 0xFF) << 8) | (blocks[7] & 0xFF);
                    }
                    if (blocks.length >= 12) {
                        float value = ByteBuffer.wrap(blocks, 8, 4).getFloat();
                        values.put(key(slot, subslot), value);
                        out.write(0x00);
                        out.flush();
                    } else {
                        float value = values.getOrDefault(key(slot, subslot), 0f);
                        out.write(ProfinetCodec.encodeFloat(value));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private static long key(int slot, int subslot) {
            return (((long) slot) << 16) | (subslot & 0xFFFF);
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
                    "test-profinet",
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
