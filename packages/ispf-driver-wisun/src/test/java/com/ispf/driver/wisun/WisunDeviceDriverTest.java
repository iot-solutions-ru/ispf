package com.ispf.driver.wisun;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.wisun.codec.WisunCoapCodec;
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

/**
 * DatagramSocket peer tests for Wi-SUN CoAP RFC 7252 (not Wi-SUN FAN PHY).
 */
class WisunDeviceDriverTest {

    private WisunDeviceDriver driver;
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
    void conGetMid1IsLiteral40010001() {
        // Handwritten CON GET, no token, MID 1 (not produced by calling the encoder for expected)
        byte[] expected = new byte[]{0x40, 0x01, 0x00, 0x01};
        assertArrayEquals(expected, WisunCoapCodec.encodeConGetNoToken(1));
        assertArrayEquals(expected, WisunDeviceDriver.buildConGetMid1());
    }

    @Test
    void ackContentMid1IsLiteral60450001() {
        // Handwritten ACK 2.05 Content, no token, MID 1, no payload
        byte[] expected = new byte[]{0x60, 0x45, 0x00, 0x01};
        assertArrayEquals(expected, WisunCoapCodec.encodeAckContentNoToken(1));
    }

    @Test
    void metadataDescribesCoapNotFanPhyWithoutLab() {
        driver = new WisunDeviceDriver();
        assertEquals("wisun", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        assertEquals("5683", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("coap"));
        assertTrue(description.contains("not") && (description.contains("fan") || description.contains("phy")));
        assertFalse(description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsNodeAndPathForms() throws Exception {
        assertEquals("/nodes/1/value", WisunPoint.parse("node:1").path());
        assertEquals("/nodes/1/value", WisunPoint.parse("/nodes/1/value").path());
        assertEquals("/nodes/2/value", WisunPoint.parse("coap:/nodes/2/value").path());
    }

    @Test
    void conGetLoopbackReceivesAck205() throws Exception {
        peer = new FakeCoapPeer();
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new WisunDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("n1", "node:1"));
        assertEquals(1.0, (Double) object.variables.get("n1").firstRow().get("value"), 0.001);
        assertEquals("/nodes/1/value", object.variables.get("n1").firstRow().get("path"));
        assertEquals(69, object.variables.get("n1").firstRow().get("code"));
        assertEquals(1, object.variables.get("n1").firstRow().get("mid"));

        assertArrayEquals(new byte[]{0x40, 0x01, 0x00, 0x01}, peer.lastRequest());
        assertArrayEquals(new byte[]{0x60, 0x45, 0x00, 0x01}, peer.lastReply());

        assertThrows(DriverException.class, () ->
                driver.writePoint("n1", object.variables.get("n1")));
    }

    private static final class FakeCoapPeer implements AutoCloseable {
        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fake-wisun-coap");
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
                // Handwritten ACK 2.05 Content MID 1, no payload
                byte[] reply = new byte[]{0x60, 0x45, 0x00, 0x01};
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
            return new PlatformObject("test-wisun", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
