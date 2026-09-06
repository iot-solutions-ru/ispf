package com.ispf.driver.opcuapubsub;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.opcuapubsub.codec.OpcuaPubsubLabCodec;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake UDP loopback tests for the OPC UA PubSub UADP-lab subset.
 * Certifies the lab dialect only — not full OPC UA PubSub / MQTT / broker / security.
 */
class OpcuaPubsubDeviceDriverTest {

    private OpcuaPubsubDeviceDriver driver;
    private FakeUadpPublisher publisher;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (publisher != null) {
            publisher.close();
            publisher = null;
        }
    }

    @Test
    void metadataDescribesUadpUdpLabNotFullPubsub() {
        driver = new OpcuaPubsubDeviceDriver();
        assertEquals("opcua-pubsub", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("4840", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("uadp") || description.contains("udp"));
        assertTrue(description.contains("not full") || description.contains("not"));
        assertTrue(description.contains("mqtt") || description.contains("broker")
                || description.contains("security"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsDatasetFieldAndNodeForms() throws Exception {
        assertEquals("ds:1", OpcuaPubsubPoint.parse("ds:1").wireToken());
        assertEquals("field:0", OpcuaPubsubPoint.parse("field:0").wireToken());
        assertEquals("ns:2;s=Temp", OpcuaPubsubPoint.parse("ns:2;s=Temp").wireToken());
    }

    @Test
    void udpGetSampleAndPublishLoopback() throws Exception {
        publisher = new FakeUadpPublisher();
        publisher.put("ds:1", 21.5);
        publisher.put("field:0", 42.0);
        publisher.put("ns:2;s=Temp", 23.75);
        publisher.start();
        assertTrue(publisher.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(publisher.port()),
                "timeoutMs", "2000"
        ));
        driver = new OpcuaPubsubDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "ds", "ds:1",
                "f0", "field:0",
                "temp", "ns:2;s=Temp"
        ));
        assertEquals(21.5, (Double) object.variables.get("ds").firstRow().get("value"), 0.001);
        assertEquals(42.0, (Double) object.variables.get("f0").firstRow().get("value"), 0.001);
        assertEquals(23.75, (Double) object.variables.get("temp").firstRow().get("value"), 0.001);

        driver.writePoint("temp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 30.5)
        ));
        assertEquals(30.5, publisher.get("ns:2;s=Temp"), 0.001);
        assertEquals(30.5, (Double) object.variables.get("temp").firstRow().get("value"), 0.001);
        assertTrue(publisher.awaitWrite(2, TimeUnit.SECONDS));
    }

    @Test
    void codecRoundTripFloatDoubleString() {
        byte[] floatFrame = OpcuaPubsubLabCodec.encodeSampleFloat("field:0", 1.25f);
        OpcuaPubsubLabCodec.LabFrame decodedFloat = OpcuaPubsubLabCodec.decode(floatFrame);
        assertEquals(OpcuaPubsubLabCodec.MSG_SAMPLE, decodedFloat.messageType());
        assertEquals(1.25, OpcuaPubsubLabCodec.decodeNumeric(decodedFloat), 0.001);

        byte[] doubleFrame = OpcuaPubsubLabCodec.encodeSample("ds:1", 99.5);
        assertEquals(99.5, OpcuaPubsubLabCodec.decodeNumeric(OpcuaPubsubLabCodec.decode(doubleFrame)), 0.001);

        byte[] stringFrame = OpcuaPubsubLabCodec.encodeSampleString("ns:2;s=Temp", "12.5");
        assertEquals(12.5, OpcuaPubsubLabCodec.decodeNumeric(OpcuaPubsubLabCodec.decode(stringFrame)), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new OpcuaPubsubDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "ds:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeUadpPublisher implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-uadp");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch writeSeen = new CountDownLatch(1);

        FakeUadpPublisher() throws IOException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return socket.getLocalPort();
        }

        void put(String key, double value) {
            values.put(normalize(key), value);
        }

        double get(String key) {
            return values.getOrDefault(normalize(key), 0.0);
        }

        void start() {
            executor.submit(() -> {
                ready.countDown();
                udpLoop();
            });
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        boolean awaitWrite(long timeout, TimeUnit unit) throws InterruptedException {
            return writeSeen.await(timeout, unit);
        }

        private void udpLoop() {
            byte[] buf = new byte[65535];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    byte[] frame = Arrays.copyOf(packet.getData(), packet.getLength());
                    OpcuaPubsubLabCodec.LabFrame parsed = OpcuaPubsubLabCodec.decode(frame);
                    String key = normalize(parsed.key());
                    byte[] response = null;
                    if (parsed.messageType() == OpcuaPubsubLabCodec.MSG_GET) {
                        double value = values.getOrDefault(key, 0.0);
                        response = OpcuaPubsubLabCodec.encodeSample(parsed.key(), value);
                    } else if (parsed.messageType() == OpcuaPubsubLabCodec.MSG_PUBLISH) {
                        values.put(key, OpcuaPubsubLabCodec.decodeNumeric(parsed));
                        writeSeen.countDown();
                        response = OpcuaPubsubLabCodec.encodeAck(parsed.key());
                    }
                    if (response != null) {
                        DatagramPacket reply = new DatagramPacket(
                                response, response.length, packet.getSocketAddress());
                        socket.send(reply);
                    }
                } catch (IOException e) {
                    if (socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private static String normalize(String key) {
            return key.trim();
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

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-opcua-pubsub",
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
