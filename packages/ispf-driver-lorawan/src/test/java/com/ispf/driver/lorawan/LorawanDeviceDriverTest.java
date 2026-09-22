package com.ispf.driver.lorawan;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.lorawan.codec.LorawanSemtechCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatagramSocket peer tests for the Semtech UDP packet-forwarder network-server driver.
 */
class LorawanDeviceDriverTest {

    private LorawanDeviceDriver driver;
    private FakeSemtechGateway gateway;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (gateway != null) {
            gateway.close();
            gateway = null;
        }
    }

    @Test
    void pushAckForToken1234IsLiteralSemtechFrame() {
        assertArrayEquals(
                new byte[]{0x02, 0x12, 0x34, 0x01},
                LorawanSemtechCodec.encodePushAck(0x1234)
        );
    }

    @Test
    void metadataDescribesSemtechUdpNotStub() {
        driver = new LorawanDeviceDriver();
        assertEquals("lorawan", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("1700", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("semtech") || description.contains("packet-forwarder"));
        assertTrue(description.contains("push_data") || description.contains("udp"));
        assertTrue(!description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsDevEuiForms() throws Exception {
        assertEquals("AABBCCDDEEFF0011", LorawanPoint.parse("AABBCCDDEEFF0011").deveui());
        assertEquals("AABBCCDDEEFF0011", LorawanPoint.parse("deveui:AABBCCDDEEFF0011").deveui());
    }

    @Test
    void pushDataUplinkAndPullRespDownlink() throws Exception {
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", "0",
                "timeoutMs", "3000"
        ));
        driver = new LorawanDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        int listenPort = object.lastBoundPort();
        assertTrue(listenPort > 0);

        gateway = new FakeSemtechGateway();
        gateway.attach(listenPort);
        gateway.startPushThenAwaitDownlink(
                "B827EBFFFE000001",
                "AABBCCDDEEFF0011",
                21.5f,
                -87.0,
                868.1
        );

        driver.readPoints(Map.of("dev", "deveui:AABBCCDDEEFF0011"));
        assertTrue(gateway.awaitPushAck(2, TimeUnit.SECONDS));
        assertArrayEquals(new byte[]{0x02, 0x12, 0x34, 0x01}, gateway.lastPushAck());
        assertEquals(21.5, (Double) object.variables.get("dev").firstRow().get("value"), 0.001);
        assertEquals("AABBCCDDEEFF0011", object.variables.get("dev").firstRow().get("deveui"));
        assertEquals(-87.0, (Double) object.variables.get("dev").firstRow().get("rssi"), 0.001);
        assertEquals(868.1, (Double) object.variables.get("dev").firstRow().get("freq"), 0.001);

        driver.writePoint("dev", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.0)
        ));
        assertTrue(gateway.awaitPullResp(2, TimeUnit.SECONDS));
        assertEquals(42.0f, gateway.lastDownlinkValue(), 0.001f);
    }

    private static final class FakeSemtechGateway implements AutoCloseable {
        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-semtech-gw");
            t.setDaemon(true);
            return t;
        });
        private final CountDownLatch pushAckSeen = new CountDownLatch(1);
        private final CountDownLatch pullRespSeen = new CountDownLatch(1);
        private final AtomicReference<byte[]> lastPushAck = new AtomicReference<>();
        private final AtomicReference<Float> lastDownlink = new AtomicReference<>();
        private int nsPort;

        FakeSemtechGateway() throws IOException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
            socket.setSoTimeout(5000);
        }

        void attach(int networkServerPort) {
            this.nsPort = networkServerPort;
        }

        void startPushThenAwaitDownlink(
                String gatewayEui,
                String deveui,
                float value,
                double rssi,
                double freq
        ) {
            var _ = executor.submit(() -> {
                try {
                    InetSocketAddress ns = new InetSocketAddress("127.0.0.1", nsPort);
                    byte[] eui = LorawanSemtechCodec.euiFromHex(gatewayEui);
                    String json = "{\"deveui\":\"" + deveui + "\",\"value\":" + value
                            + ",\"rssi\":" + rssi + ",\"freq\":" + freq
                            + ",\"data\":\"" + value + "\"}";
                    byte[] push = LorawanSemtechCodec.encodePushData(0x1234, eui, json);
                    socket.send(new DatagramPacket(push, push.length, ns));

                    byte[] buf = new byte[2048];
                    DatagramPacket reply = new DatagramPacket(buf, buf.length);
                    socket.receive(reply);
                    byte[] ack = Arrays.copyOf(reply.getData(), reply.getLength());
                    lastPushAck.set(ack);
                    pushAckSeen.countDown();

                    socket.receive(reply);
                    byte[] pullResp = Arrays.copyOf(reply.getData(), reply.getLength());
                    LorawanSemtechCodec.SemtechPacket parsed = LorawanSemtechCodec.decode(pullResp);
                    if (parsed.identifier() == LorawanSemtechCodec.PULL_RESP) {
                        String body = parsed.json();
                        int idx = body.indexOf("\"value\":");
                        if (idx >= 0) {
                            String rest = body.substring(idx + 8).trim();
                            int end = 0;
                            while (end < rest.length()
                                    && (Character.isDigit(rest.charAt(end))
                                    || rest.charAt(end) == '.'
                                    || rest.charAt(end) == '-')) {
                                end++;
                            }
                            lastDownlink.set(Float.parseFloat(rest.substring(0, end)));
                        }
                        pullRespSeen.countDown();
                    }
                } catch (Exception ignored) {
                    // closed / timeout
                }
            });
        }

        boolean awaitPushAck(long timeout, TimeUnit unit) throws InterruptedException {
            return pushAckSeen.await(timeout, unit);
        }

        boolean awaitPullResp(long timeout, TimeUnit unit) throws InterruptedException {
            return pullRespSeen.await(timeout, unit);
        }

        byte[] lastPushAck() {
            return lastPushAck.get();
        }

        float lastDownlinkValue() {
            Float v = lastDownlink.get();
            return v == null ? 0f : v;
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
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();
        private volatile int boundPort;

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        int lastBoundPort() {
            return boundPort;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-lorawan",
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
            if (message != null && message.contains("bound on ")) {
                int colon = message.lastIndexOf(':');
                if (colon > 0) {
                    try {
                        boundPort = Integer.parseInt(message.substring(colon + 1).trim());
                    } catch (NumberFormatException ignored) {
                        // leave previous
                    }
                }
            }
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
