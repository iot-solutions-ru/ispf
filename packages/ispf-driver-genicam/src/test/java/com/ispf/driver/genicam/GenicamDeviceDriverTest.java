package com.ispf.driver.genicam;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.genicam.codec.GenicamCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenicamDeviceDriverTest {

    private GenicamDeviceDriver driver;
    private FakeGvcpPeer peer;

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
    void discoveryHeaderLiteralStarts42010002() {
        // Handwritten GVCP discovery header (not produced by the encoder)
        byte[] expected = new byte[] { 0x42, 0x01, 0x00, 0x02 };
        byte[] encoded = GenicamDeviceDriver.buildDiscoveryCommand(1);
        assertArrayEquals(expected, Arrays.copyOf(encoded, 4));
        assertArrayEquals(expected, Arrays.copyOf(GenicamCodec.encodeDiscoveryCommand(7), 4));
    }

    @Test
    void metadataIsProductionReadOnlyWithoutLab() {
        driver = new GenicamDeviceDriver();
        assertEquals("genicam", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("gvcp") || description.contains("discovery"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void discoveryLoopbackOverUdp() throws Exception {
        peer = new FakeGvcpPeer();
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new GenicamDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("cam", "discovery"));
        assertEquals("ok", object.variables.get("cam").firstRow().get("value"));
        assertTrue(String.valueOf(object.variables.get("cam").firstRow().get("raw")).startsWith("0000"));

        byte[] captured = peer.lastRequest();
        assertArrayEquals(new byte[] { 0x42, 0x01, 0x00, 0x02 }, Arrays.copyOf(captured, 4));

        assertThrows(DriverException.class, () ->
                driver.writePoint("cam", object.variables.get("cam")));
    }

    private static final class FakeGvcpPeer implements AutoCloseable {
        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fake-gvcp");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<byte[]> lastRequest = new AtomicReference<>(new byte[0]);

        FakeGvcpPeer() throws Exception {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
            socket.setSoTimeout(5000);
        }

        int port() {
            return socket.getLocalPort();
        }

        byte[] lastRequest() {
            return lastRequest.get();
        }

        void start() {
            executor.submit(this::serve);
        }

        private void serve() {
            try {
                byte[] buffer = new byte[1500];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byte[] request = Arrays.copyOf(packet.getData(), packet.getLength());
                lastRequest.set(request);
                // Truncated GVCP discovery ACK header (key 0x00, command 0x0003), not a full device payload
                byte[] reply = new byte[] {
                        0x00, 0x00, 0x00, 0x03, 0x00, 0x00,
                        request.length > 7 ? request[6] : 0x00,
                        request.length > 7 ? request[7] : 0x01
                };
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
            return new PlatformObject("test-genicam", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
