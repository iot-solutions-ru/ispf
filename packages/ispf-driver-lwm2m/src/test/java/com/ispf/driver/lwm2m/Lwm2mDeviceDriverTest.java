package com.ispf.driver.lwm2m;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatagramSocket peer tests for LwM2M over CoAP (not a full bootstrap/observe stack).
 */
class Lwm2mDeviceDriverTest {

    /**
     * Handwritten CON GET /3/0/0, MID 1, no token, three Uri-Path options
     * (delta 11/'3', delta 0/'0', delta 0/'0') — source of truth, not from the encoder.
     */
    private static final byte[] DEVICE_300_GET = new byte[]{
            0x40, 0x01, 0x00, 0x01,
            (byte) 0xB1, 0x33,
            0x01, 0x30,
            0x01, 0x30
    };

    /**
     * Handwritten ACK 2.05 Content, MID 1, no token, payload marker + ASCII OK.
     */
    private static final byte[] ACK_205_OK = new byte[]{
            0x60, 0x45, 0x00, 0x01,
            (byte) 0xFF, 0x4F, 0x4B
    };

    private Lwm2mDeviceDriver driver;
    private FakeCoapPeer peer;

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
    void device300ConGetMatchesHandwrittenLiteral() {
        assertArrayEquals(DEVICE_300_GET, CoapGetClient.encodeConGetDevice300());
        assertArrayEquals(DEVICE_300_GET, Lwm2mDeviceDriver.buildDevice300Get());
        assertArrayEquals(DEVICE_300_GET, CoapGetClient.buildGet(1, java.util.List.of("3", "0", "0")));
        assertArrayEquals(DEVICE_300_GET, Lwm2mDeviceDriver.encodePathGet("/3/0/0"));
    }

    @Test
    void ack205OkMatchesHandwrittenLiteral() {
        assertArrayEquals(ACK_205_OK, CoapGetClient.encodeAckContentOkMid1());
    }

    @Test
    void metadataDescribesCoapLwm2mNotFullBootstrapObserveWithoutLab() {
        driver = new Lwm2mDeviceDriver();
        assertEquals("lwm2m", driver.metadata().id());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("coap"));
        assertTrue(description.contains("lwm2m"));
        assertTrue(description.contains("not") && description.contains("bootstrap")
                && description.contains("observe"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void device300LoopbackReceivesAck205Ok() throws Exception {
        peer = new FakeCoapPeer();
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000",
                "probeRegisterOnConnect", "false"
        ));
        driver = new Lwm2mDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("device", "/3/0/0"));
        assertEquals("OK", object.variables.get("device").firstRow().get("value"));
        assertEquals("/3/0/0", object.variables.get("device").firstRow().get("path"));
        assertEquals(69, object.variables.get("device").firstRow().get("code"));

        assertArrayEquals(DEVICE_300_GET, peer.lastRequest());
        assertArrayEquals(ACK_205_OK, peer.lastReply());
    }

    @Test
    void writeIsReadOnly() throws Exception {
        peer = new FakeCoapPeer();
        peer.start();

        driver = new Lwm2mDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "probeRegisterOnConnect", "false"
        )));
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("x", null));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("read-only"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new Lwm2mDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of("probeRegisterOnConnect", "false")));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("m", "/3/0/0")));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("not connected"));
    }

    @Test
    void connectFailsWhenServerSilent() throws Exception {
        int closedPort;
        try (ServerSocket tcp = new ServerSocket(0)) {
            closedPort = tcp.getLocalPort();
        }
        driver = new Lwm2mDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200",
                "probeRegisterOnConnect", "true"
        )));
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("lwm2m connect probe failed"));
    }

    @Test
    void pathNormalization() {
        assertEquals("/3/0/0", CoapGetClient.normalizePath("3/0/0"));
        assertEquals("/rd", CoapGetClient.normalizePath("/rd"));
        assertEquals(3, CoapGetClient.pathSegments("/3/0/0").size());
    }

    private static final class FakeCoapPeer implements AutoCloseable {
        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fake-lwm2m-coap");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<byte[]> lastRequest = new AtomicReference<>(new byte[0]);
        private final AtomicReference<byte[]> lastReply = new AtomicReference<>(new byte[0]);

        FakeCoapPeer() throws Exception {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
            socket.setSoTimeout(5000);
        }

        int port() {
            return socket.getLocalPort();
        }

        byte[] lastRequest() {
            return lastRequest.get();
        }

        byte[] lastReply() {
            return lastReply.get();
        }

        void start() {
            var _ = executor.submit(this::serve);
        }

        private void serve() {
            try {
                byte[] buffer = new byte[1500];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byte[] request = Arrays.copyOf(packet.getData(), packet.getLength());
                lastRequest.set(request);
                // Handwritten ACK 2.05 Content MID 1, payload OK
                byte[] reply = Arrays.copyOf(ACK_205_OK, ACK_205_OK.length);
                lastReply.set(reply);
                socket.send(new DatagramPacket(reply, reply.length, packet.getSocketAddress()));
            } catch (Exception ignored) {
                // closed
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        final Map<String, DataRecord> variables = new java.util.concurrent.ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-lwm2m",
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
