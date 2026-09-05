package com.ispf.driver.knxtp;

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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link KnxTpDeviceDriver} against a fake KNXnet/IP Routing UDP peer.
 */
class KnxTpDeviceDriverTest {

    private KnxTpDeviceDriver driver;
    private FakeKnxRoutingPeer peer;

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
    void readsAndWritesGroupAddressesViaRouting() throws Exception {
        peer = new FakeKnxRoutingPeer();
        peer.put(KnxTpPoint.parse("1/2/3").groupAddress(), 0x42);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new KnxTpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("light", "1/2/3"));
        DataRecord light = object.variables.get("light");
        assertEquals("66", light.firstRow().get("value"));
        assertEquals("1/2/3", light.firstRow().get("groupAddress"));
        assertEquals(0x42, ((Number) light.firstRow().get("raw")).intValue());

        driver.writePoint("light", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 0x11)
        ));
        assertEquals(0x11, peer.get(KnxTpPoint.parse("1/2/3").groupAddress()));
        assertEquals("17", object.variables.get("light").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(0x0A03, KnxTpPoint.parse("1/2/3").groupAddress());
        assertEquals(new KnxTpPoint(1, 2, 3, true), KnxTpPoint.parse("1/2/3"));
        assertEquals(new KnxTpPoint(1, 0, 5, false), KnxTpPoint.parse("1/5"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new KnxTpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "1/2/3")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void metadataIdIsKnxTp() {
        assertEquals("knx-tp", new KnxTpDeviceDriver().metadata().id());
        assertTrue(new KnxTpDeviceDriver().metadata().supportsWrite());
    }

    private static final class FakeKnxRoutingPeer implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-knx-routing");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Integer> values = new ConcurrentHashMap<>();
        private final AtomicBoolean running = new AtomicBoolean();

        FakeKnxRoutingPeer() throws IOException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return socket.getLocalPort();
        }

        void put(int groupAddress, int value) {
            values.put(groupAddress & 0xFFFF, value & 0xFF);
        }

        int get(int groupAddress) {
            return values.getOrDefault(groupAddress & 0xFFFF, 0);
        }

        void start() {
            running.set(true);
            executor.submit(this::loop);
        }

        private void loop() {
            byte[] buffer = new byte[512];
            while (running.get() && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byte[] frame = Arrays.copyOf(packet.getData(), packet.getLength());
                    handle(frame, packet);
                } catch (IOException e) {
                    if (!running.get() || socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(byte[] frame, DatagramPacket request) throws IOException {
            if (frame.length < 16) {
                return;
            }
            int service = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
            if (service != KnxTpDeviceDriver.SERVICE_ROUTING_INDICATION) {
                return;
            }
            int cemiOffset = 6;
            int addInfo = frame[cemiOffset + 1] & 0xFF;
            int base = cemiOffset + 2 + addInfo;
            int ga = ((frame[base + 4] & 0xFF) << 8) | (frame[base + 5] & 0xFF);
            int lengthField = frame[base + 6] & 0xFF;
            int apci = ((frame[base + 7] & 0xFF) << 8) | (frame[base + 8] & 0xFF);
            int apciType = apci & 0x03C0;
            if (apciType == KnxTpDeviceDriver.APCI_GROUP_WRITE) {
                int value = lengthField <= 2 ? (frame[base + 8] & 0x3F) : (frame[base + 9] & 0xFF);
                values.put(ga, value & 0xFF);
                return;
            }
            if (apciType == KnxTpDeviceDriver.APCI_GROUP_READ) {
                int value = values.getOrDefault(ga, 0);
                byte[] cemi = KnxTpDeviceDriver.buildCemi(
                        KnxTpDeviceDriver.CEMI_L_DATA_IND,
                        ga,
                        KnxTpDeviceDriver.APCI_GROUP_RESPONSE,
                        new byte[] { (byte) value }
                );
                byte[] reply = KnxTpDeviceDriver.wrapRouting(cemi);
                socket.send(new DatagramPacket(reply, reply.length, request.getAddress(), request.getPort()));
            }
        }

        @Override
        public void close() throws Exception {
            running.set(false);
            socket.close();
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
                    "test-knx-tp",
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
