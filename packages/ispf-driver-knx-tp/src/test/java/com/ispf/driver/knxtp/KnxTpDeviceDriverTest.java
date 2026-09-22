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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link KnxTpDeviceDriver} against a fake KNXnet/IP UDP peer.
 */
class KnxTpDeviceDriverTest {

    private KnxTpDeviceDriver driver;
    private FakeKnxnetIpPeer peer;

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
    void searchRequestBytesAreExact() {
        assertArrayEquals(new byte[] {
                0x06, 0x10, 0x02, 0x01, 0x00, 0x0E, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        }, KnxTpDeviceDriver.SEARCH_REQUEST);
    }

    @Test
    void searchConnectReadWriteGroupAddresses() throws Exception {
        peer = new FakeKnxnetIpPeer();
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
        assertArrayEquals(KnxTpDeviceDriver.SEARCH_REQUEST, peer.firstSearchBytes.get());
        assertTrue(peer.searchCount.get() >= 1);
        assertTrue(peer.connectCount.get() >= 1);

        driver.readPoints(Map.of("light", "1/2/3"));
        DataRecord light = object.variables.get("light");
        assertEquals("66", light.firstRow().get("value"));
        assertEquals("1/2/3", light.firstRow().get("groupAddress"));
        assertEquals(0x42, ((Number) light.firstRow().get("raw")).intValue());

        driver.writePoint("light", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 0x11)
        ));
        int ga = KnxTpPoint.parse("1/2/3").groupAddress();
        long deadline = System.currentTimeMillis() + 2000;
        while (peer.get(ga) != 0x11 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0x11, peer.get(ga));
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
    void metadataNamesKnxnetIpWithoutLab() {
        assertEquals("knx-tp", new KnxTpDeviceDriver().metadata().id());
        assertTrue(new KnxTpDeviceDriver().metadata().supportsWrite());
        String desc = new KnxTpDeviceDriver().metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(desc.contains("knxnet/ip"));
        assertTrue(!desc.contains("lab"));
        assertTrue(!desc.contains("twisted-pair phy") && !desc.contains("tp phy"));
    }

    private static final class FakeKnxnetIpPeer implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-knxnet-ip");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Integer> values = new ConcurrentHashMap<>();
        private final AtomicBoolean running = new AtomicBoolean();
        final AtomicInteger searchCount = new AtomicInteger();
        final AtomicInteger connectCount = new AtomicInteger();
        final AtomicReference<byte[]> firstSearchBytes = new AtomicReference<>();
        private final AtomicInteger channelSeq = new AtomicInteger();
        private volatile int channelId = 17;

        FakeKnxnetIpPeer() throws IOException {
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
                    handle(frame, packet.getAddress(), packet.getPort());
                } catch (IOException e) {
                    if (!running.get() || socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(byte[] frame, InetAddress address, int port) throws IOException {
            int service = KnxTpDeviceDriver.serviceType(frame);
            if (service == KnxTpDeviceDriver.SERVICE_SEARCH_REQUEST) {
                searchCount.incrementAndGet();
                if (firstSearchBytes.get() == null) {
                    firstSearchBytes.set(Arrays.copyOf(frame, frame.length));
                }
                byte[] hpai = KnxTpDeviceDriver.udpHpai(new byte[] { 127, 0, 0, 1 }, this.port());
                byte[] dib = new byte[] {
                        0x0E, 0x01,
                        0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                };
                int total = 6 + hpai.length + dib.length;
                byte[] resp = new byte[total];
                writeHeader(resp, KnxTpDeviceDriver.SERVICE_SEARCH_RESPONSE, total);
                System.arraycopy(hpai, 0, resp, 6, hpai.length);
                System.arraycopy(dib, 0, resp, 6 + hpai.length, dib.length);
                send(resp, address, port);
                return;
            }
            if (service == KnxTpDeviceDriver.SERVICE_CONNECT_REQUEST) {
                connectCount.incrementAndGet();
                channelId = 17;
                byte[] hpai = KnxTpDeviceDriver.udpHpai(new byte[] { 127, 0, 0, 1 }, this.port());
                byte[] crd = new byte[] { 0x04, 0x04, 0x11, 0x01 };
                int total = 6 + 2 + hpai.length + crd.length;
                byte[] resp = new byte[total];
                writeHeader(resp, KnxTpDeviceDriver.SERVICE_CONNECT_RESPONSE, total);
                resp[6] = (byte) channelId;
                resp[7] = 0x00;
                System.arraycopy(hpai, 0, resp, 8, hpai.length);
                System.arraycopy(crd, 0, resp, 8 + hpai.length, crd.length);
                send(resp, address, port);
                return;
            }
            if (service == KnxTpDeviceDriver.SERVICE_TUNNELLING_REQUEST && frame.length >= 10) {
                int ch = frame[7] & 0xFF;
                int seq = frame[8] & 0xFF;
                send(KnxTpDeviceDriver.tunnellingAck(ch, seq, 0), address, port);
                byte[] cemi = Arrays.copyOfRange(frame, 10, frame.length);
                if (cemi.length < 11) {
                    return;
                }
                int addInfo = cemi[1] & 0xFF;
                int base = 2 + addInfo;
                int ga = ((cemi[base + 4] & 0xFF) << 8) | (cemi[base + 5] & 0xFF);
                int lengthField = cemi[base + 6] & 0xFF;
                int apci = ((cemi[base + 7] & 0xFF) << 8) | (cemi[base + 8] & 0xFF);
                int apciType = apci & 0x03C0;
                if (apciType == KnxTpDeviceDriver.APCI_GROUP_WRITE) {
                    int value = lengthField <= 2 ? (cemi[base + 8] & 0x3F) : (cemi[base + 9] & 0xFF);
                    values.put(ga, value & 0xFF);
                    return;
                }
                if (apciType == KnxTpDeviceDriver.APCI_GROUP_READ) {
                    int value = values.getOrDefault(ga, 0);
                    byte[] responseCemi = KnxTpDeviceDriver.buildCemi(
                            KnxTpDeviceDriver.CEMI_L_DATA_IND,
                            ga,
                            KnxTpDeviceDriver.APCI_GROUP_RESPONSE,
                            new byte[] { (byte) value }
                    );
                    int indSeq = channelSeq.getAndIncrement() & 0xFF;
                    send(KnxTpDeviceDriver.tunnellingRequest(channelId, indSeq, responseCemi), address, port);
                }
            }
        }

        private static void writeHeader(byte[] frame, int service, int total) {
            frame[0] = 0x06;
            frame[1] = 0x10;
            frame[2] = (byte) ((service >> 8) & 0xFF);
            frame[3] = (byte) (service & 0xFF);
            frame[4] = (byte) ((total >> 8) & 0xFF);
            frame[5] = (byte) (total & 0xFF);
        }

        private void send(byte[] frame, InetAddress address, int port) throws IOException {
            socket.send(new DatagramPacket(frame, frame.length, address, port));
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
