package com.ispf.driver.controlnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.controlnet.codec.ControlnetCodec;
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

class ControlnetDeviceDriverTest {

    private ControlnetDeviceDriver driver;
    private FakeControlnetPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (peer != null) { peer.close(); peer = null; }
    }

    @Test
    void metadataDescribesCipOverTcp() {
        driver = new ControlnetDeviceDriver();
        assertEquals("controlnet", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("cip") || description.contains("get_attribute"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserMapsToClassInstanceAttribute() throws Exception {
        ControlnetPoint slot = ControlnetPoint.parse("slot:0");
        assertEquals(1, slot.cipClass());
        assertEquals(0, slot.instance());
        assertEquals(1, slot.attribute());

        ControlnetPoint ch = ControlnetPoint.parse("slot:0:ch:1");
        assertEquals(0, ch.instance());
        assertEquals(1, ch.attribute());
    }

    @Test
    void readAndWriteExplicitMessage() throws Exception {
        peer = new FakeControlnetPeer();
        peer.put(1, 2, 1, 21.0f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new ControlnetDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("n", "node:2"));
        assertEquals(21.0, (Double) object.variables.get("n").firstRow().get("value"), 0.001);

        driver.writePoint("n", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 8.0)
        ));
        assertEquals(8.0f, peer.get(1, 2, 1), 0.001f);
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new ControlnetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        assertThrows(DriverException.class, () -> driver.readPoints(Map.of("x", "slot:0")));
    }

    private static final class FakeControlnetPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-controlnet");
            t.setDaemon(true);
            return t;
        });
        private final Map<Long, Float> values = new ConcurrentHashMap<>();

        FakeControlnetPeer() throws IOException {
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
                    byte[] header = ControlnetCodec.readFully(in, 8);
                    int service = header[0] & 0xFF;
                    int cipClass = header[3] & 0xFF;
                    int instance = header[5] & 0xFF;
                    int attribute = header[7] & 0xFF;
                    if (service == ControlnetCodec.GET_ATTRIBUTE_SINGLE) {
                        float value = values.getOrDefault(key(cipClass, instance, attribute), 0f);
                        out.write(ControlnetCodec.GET_ATTRIBUTE_SINGLE_REPLY);
                        out.write(ControlnetCodec.encodeFloat(value));
                        out.flush();
                    } else if (service == ControlnetCodec.SET_ATTRIBUTE_SINGLE) {
                        byte[] data = ControlnetCodec.readFully(in, 4);
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
