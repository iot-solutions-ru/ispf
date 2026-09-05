package com.ispf.driver.knx;

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
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link KnxDeviceDriver} against an in-process fake KNXnet/IP router.
 */
class KnxDeviceDriverTest {

    private KnxDeviceDriver driver;
    private FakeKnxRouter router;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (router != null) {
            router.close();
            router = null;
        }
    }

    @Test
    void searchConnectReadWriteGroupAddress() throws Exception {
        router = new FakeKnxRouter();
        router.put(KnxnetIpCodec.parseGroupAddress("1/2/3"), 5);
        router.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(router.port()),
                "timeoutMs", "2000",
                "searchOnConnect", "true"
        ));
        driver = new KnxDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertTrue(router.searchCount() >= 1);
        assertTrue(router.connectCount() >= 1);

        driver.readPoints(Map.of("light", "1/2/3"));
        assertEquals("5", object.variables.get("light").firstRow().get("value"));
        assertEquals("1/2/3", object.variables.get("light").firstRow().get("groupAddress"));

        driver.writePoint("light", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "1")
        ));
        assertEquals(1, router.get(KnxnetIpCodec.parseGroupAddress("1/2/3")));
        assertEquals("1", object.variables.get("light").firstRow().get("value"));

        driver.readPoints(Map.of("light", "1/2/3"));
        assertEquals("1", object.variables.get("light").firstRow().get("value"));
    }

    @Test
    void parseGroupAddress1_2_3() {
        assertEquals(0x0A03, KnxnetIpCodec.parseGroupAddress("1/2/3"));
        assertEquals("1/2/3", KnxnetIpCodec.formatGroupAddress(0x0A03));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new KnxDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("g", "1/2/3")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void connectFailsAgainstSilentPort() throws Exception {
        int closedPort;
        try (ServerSocket tcp = new ServerSocket(0)) {
            closedPort = tcp.getLocalPort();
        }
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200",
                "searchOnConnect", "true"
        ));
        driver = new KnxDeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("KNX"));
    }

    private static final class FakeKnxRouter implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-knx-router");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Integer> groupValues = new ConcurrentHashMap<>();
        private final AtomicInteger searchCount = new AtomicInteger();
        private final AtomicInteger connectCount = new AtomicInteger();
        private final AtomicInteger channelSeq = new AtomicInteger();
        private volatile int channelId = 1;
        private volatile boolean running = true;

        FakeKnxRouter() throws IOException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return socket.getLocalPort();
        }

        void put(int ga, int value) {
            groupValues.put(ga, value);
        }

        int get(int ga) {
            return groupValues.getOrDefault(ga, 0);
        }

        int searchCount() {
            return searchCount.get();
        }

        int connectCount() {
            return connectCount.get();
        }

        void start() {
            executor.submit(this::loop);
        }

        private void loop() {
            byte[] buf = new byte[512];
            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    handle(data, packet.getAddress(), packet.getPort());
                } catch (IOException e) {
                    if (!running || socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(byte[] frame, InetAddress address, int port) throws IOException {
            int service = KnxnetIpCodec.serviceType(frame);
            if (service == KnxnetIpCodec.SERVICE_SEARCH_REQUEST) {
                searchCount.incrementAndGet();
                byte[] hpai = KnxnetIpCodec.udpHpai(new byte[]{127, 0, 0, 1}, this.port());
                byte[] dib = new byte[]{
                        0x0E, 0x01,
                        0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                };
                int total = 6 + hpai.length + dib.length;
                byte[] resp = new byte[total];
                ByteBufferHeader.write(resp, KnxnetIpCodec.SERVICE_SEARCH_RESPONSE, total);
                System.arraycopy(hpai, 0, resp, 6, hpai.length);
                System.arraycopy(dib, 0, resp, 6 + hpai.length, dib.length);
                send(resp, address, port);
                return;
            }
            if (service == KnxnetIpCodec.SERVICE_CONNECT_REQUEST) {
                connectCount.incrementAndGet();
                channelId = 17;
                byte[] hpai = KnxnetIpCodec.udpHpai(new byte[]{127, 0, 0, 1}, this.port());
                byte[] crd = new byte[]{0x04, 0x04, 0x11, 0x01};
                int total = 6 + 2 + hpai.length + crd.length;
                byte[] resp = new byte[total];
                ByteBufferHeader.write(resp, KnxnetIpCodec.SERVICE_CONNECT_RESPONSE, total);
                resp[6] = (byte) channelId;
                resp[7] = 0x00;
                System.arraycopy(hpai, 0, resp, 8, hpai.length);
                System.arraycopy(crd, 0, resp, 8 + hpai.length, crd.length);
                send(resp, address, port);
                return;
            }
            if (service == KnxnetIpCodec.SERVICE_TUNNELLING_REQUEST) {
                KnxnetIpCodec.TunnellingFrame tun = KnxnetIpCodec.parseTunnelling(frame);
                send(KnxnetIpCodec.tunnellingAck(tun.channelId(), tun.sequence(), 0), address, port);
                byte[] cemi = tun.cemi();
                if (KnxnetIpCodec.isGroupValueRead(cemi)) {
                    int ga = KnxnetIpCodec.extractGroupAddressFromCemi(cemi);
                    int value = groupValues.getOrDefault(ga, 0);
                    int seq = channelSeq.getAndIncrement() & 0xFF;
                    byte[] ind = KnxnetIpCodec.groupValueResponseCemi(ga, value);
                    send(KnxnetIpCodec.tunnellingRequest(channelId, seq, ind), address, port);
                } else if (KnxnetIpCodec.isGroupValueWrite(cemi)) {
                    int ga = KnxnetIpCodec.extractGroupAddressFromCemi(cemi);
                    Integer value = KnxnetIpCodec.extractGroupValue(cemi);
                    if (value != null) {
                        groupValues.put(ga, value);
                    }
                }
            }
        }

        private void send(byte[] frame, InetAddress address, int port) throws IOException {
            socket.send(new DatagramPacket(frame, frame.length, address, port));
        }

        @Override
        public void close() {
            running = false;
            socket.close();
            executor.shutdownNow();
        }
    }

    private static final class ByteBufferHeader {
        static void write(byte[] frame, int service, int total) {
            frame[0] = 0x06;
            frame[1] = 0x10;
            frame[2] = (byte) ((service >> 8) & 0xFF);
            frame[3] = (byte) (service & 0xFF);
            frame[4] = (byte) ((total >> 8) & 0xFF);
            frame[5] = (byte) (total & 0xFF);
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-knx",
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
