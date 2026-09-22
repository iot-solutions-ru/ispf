package com.ispf.driver.devicenet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.devicenet.codec.DeviceNetCodec;
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

class DeviceNetDeviceDriverTest {

    private DeviceNetDeviceDriver driver;
    private FakeDeviceNetPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (peer != null) { peer.close(); peer = null; }
    }

    @Test
    void metadataDescribesCipOverTcp() {
        driver = new DeviceNetDeviceDriver();
        assertEquals("device-net", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("cip") || description.contains("get_attribute"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserMapsToClassInstanceAttribute() throws Exception {
        DeviceNetPoint node = DeviceNetPoint.parse("node:1");
        assertEquals(1, node.cipClass());
        assertEquals(1, node.instance());
        assertEquals(1, node.attribute());

        DeviceNetPoint path = DeviceNetPoint.parse("class:4:inst:1:attr:3");
        assertEquals(4, path.cipClass());
        assertEquals(1, path.instance());
        assertEquals(3, path.attribute());
    }

    @Test
    void readAndWriteExplicitMessage() throws Exception {
        peer = new FakeDeviceNetPeer();
        peer.put(1, 1, 1, 12.5f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new DeviceNetDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("n", "node:1"));
        assertEquals(12.5, (Double) object.variables.get("n").firstRow().get("value"), 0.001);

        driver.writePoint("n", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 3.5)
        ));
        assertEquals(3.5f, peer.get(1, 1, 1), 0.001f);
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new DeviceNetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("x", "node:1")));
    }

    private static final class FakeDeviceNetPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-devicenet");
            t.setDaemon(true);
            return t;
        });
        private final Map<Long, Float> values = new ConcurrentHashMap<>();

        FakeDeviceNetPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() { return serverSocket.getLocalPort(); }
        void put(int c, int i, int a, float v) { values.put(key(c, i, a), v); }
        float get(int c, int i, int a) { return values.getOrDefault(key(c, i, a), 0f); }
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
                    byte[] header = DeviceNetCodec.readFully(in, 8);
                    int service = header[0] & 0xFF;
                    int cipClass = header[3] & 0xFF;
                    int instance = header[5] & 0xFF;
                    int attribute = header[7] & 0xFF;
                    if (service == DeviceNetCodec.GET_ATTRIBUTE_SINGLE) {
                        float value = values.getOrDefault(key(cipClass, instance, attribute), 0f);
                        out.write(DeviceNetCodec.GET_ATTRIBUTE_SINGLE_REPLY);
                        out.write(DeviceNetCodec.encodeFloat(value));
                        out.flush();
                    } else if (service == DeviceNetCodec.SET_ATTRIBUTE_SINGLE) {
                        byte[] data = DeviceNetCodec.readFully(in, 4);
                        values.put(key(cipClass, instance, attribute), ByteBuffer.wrap(data).getFloat());
                        out.write(0x90);
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private static long key(int c, int i, int a) {
            return (((long) c) << 32) | (((long) i) << 16) | (a & 0xFFFF);
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
