package com.ispf.driver.ethernetpowerlink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake UDP loopback tests for the Ethernet POWERLINK MN/CN lab subset.
 * Certifies the lab dialect only — not full EPSG POWERLINK MN with hard RT.
 */
class EthernetPowerlinkDeviceDriverTest {

    private EthernetPowerlinkDeviceDriver driver;
    private FakePowerlinkMnLab lab;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (lab != null) {
            lab.close();
            lab = null;
        }
    }

    @Test
    void metadataIsProductionReadWritePdoUdpLab() {
        driver = new EthernetPowerlinkDeviceDriver();
        assertEquals("ethernet-powerlink", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("6040", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("pdo") || description.contains("udp")
                || description.contains("powerlink"));
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("not") || description.contains("not full"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsObjectAndPdoForms() throws Exception {
        EthernetPowerlinkPoint object = EthernetPowerlinkPoint.parse("node:1:obj:0x6000:01");
        assertEquals("node:1:obj:0x6000:01", object.wireToken());
        assertEquals(EthernetPowerlinkPoint.Kind.OBJECT, object.kind());

        EthernetPowerlinkPoint pdo = EthernetPowerlinkPoint.parse("pdo:1");
        assertEquals("pdo:1", pdo.wireToken());
        assertEquals(EthernetPowerlinkPoint.Kind.PDO, pdo.kind());
    }

    @Test
    void udpGetAndSetLoopback() throws Exception {
        lab = new FakePowerlinkMnLab();
        lab.put("node:1:obj:0x6000:01", 12.5);
        lab.put("pdo:1", 21.0);
        lab.start();
        assertTrue(lab.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(lab.port()),
                "timeoutMs", "2000"
        ));
        driver = new EthernetPowerlinkDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "obj", "node:1:obj:0x6000:01",
                "pdo", "pdo:1"
        ));
        assertEquals(12.5, (Double) object.variables.get("obj").firstRow().get("value"), 0.001);
        assertEquals(21.0, (Double) object.variables.get("pdo").firstRow().get("value"), 0.001);

        driver.writePoint("pdo", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.25)
        ));
        assertEquals(33.25, lab.get("pdo:1"), 0.001);
        assertEquals(33.25, (Double) object.variables.get("pdo").firstRow().get("value"), 0.001);
        assertTrue(lab.awaitWrite(2, TimeUnit.SECONDS));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new EthernetPowerlinkDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "pdo:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakePowerlinkMnLab implements AutoCloseable {

        private final DatagramSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-powerlink");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final AtomicInteger writes = new AtomicInteger();
        private final CountDownLatch writeSeen = new CountDownLatch(1);
        private volatile boolean running = true;

        FakePowerlinkMnLab() throws IOException {
            socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
            socket.setSoTimeout(500);
        }

        int port() {
            return socket.getLocalPort();
        }

        void put(String token, double value) {
            values.put(normalize(token), value);
        }

        double get(String token) {
            return values.getOrDefault(normalize(token), 0.0);
        }

        void start() {
            executor.submit(this::loop);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        boolean awaitWrite(long timeout, TimeUnit unit) throws InterruptedException {
            return writeSeen.await(timeout, unit);
        }

        private void loop() {
            byte[] buf = new byte[2048];
            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String request = new String(
                            packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII)
                            .trim();
                    String upper = request.toUpperCase(Locale.ROOT);
                    String reply;
                    if (upper.startsWith("GET ")) {
                        String token = normalize(request.substring(4).trim());
                        Double value = values.get(token);
                        reply = "VALUE " + (value == null ? 0.0 : value);
                    } else if (upper.startsWith("SET ")) {
                        String rest = request.substring(4).trim();
                        int space = rest.lastIndexOf(' ');
                        if (space < 0) {
                            reply = "ERR";
                        } else {
                            String token = normalize(rest.substring(0, space).trim());
                            double value = Double.parseDouble(rest.substring(space + 1).trim());
                            values.put(token, value);
                            writes.incrementAndGet();
                            writeSeen.countDown();
                            reply = "OK";
                        }
                    } else {
                        reply = "ERR";
                    }
                    byte[] out = reply.getBytes(StandardCharsets.US_ASCII);
                    socket.send(new DatagramPacket(
                            out, out.length, packet.getAddress(), packet.getPort()));
                } catch (IOException e) {
                    if (!running || socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private static String normalize(String token) {
            return token.trim().toLowerCase(Locale.ROOT);
        }

        @Override
        public void close() throws Exception {
            running = false;
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
                    "test-ethernet-powerlink",
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
