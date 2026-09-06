package com.ispf.driver.foundationfieldbus;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP loopback tests for the Foundation Fieldbus HSE/TCP gateway lab.
 * Certifies the lab dialect only — not native H1 / LAS / Fieldbus Foundation stack.
 */
class FoundationFieldbusDeviceDriverTest {

    private FoundationFieldbusDeviceDriver driver;
    private FakeFoundationFieldbusGateway gateway;

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
    void metadataIsProductionReadWriteHseTcpGatewayLab() {
        driver = new FoundationFieldbusDeviceDriver();
        assertEquals("foundation-fieldbus", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("hse") || description.contains("tcp") || description.contains("gateway"));
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
        assertEquals("1089", driver.metadata().configurationSchema().get("port"));
    }

    @Test
    void pointParserAcceptsAiAoDevicePvAndFfForms() throws Exception {
        FoundationFieldbusPoint ai = FoundationFieldbusPoint.parse("ai:1");
        assertEquals("ai:1", ai.wireToken());
        assertEquals("ai", ai.kind());
        assertEquals(1, ai.index());

        FoundationFieldbusPoint ao = FoundationFieldbusPoint.parse("ao:2");
        assertEquals("ao:2", ao.wireToken());
        assertEquals(2, ao.index());

        FoundationFieldbusPoint devicePv = FoundationFieldbusPoint.parse("device:0:pv");
        assertEquals("device:0:pv", devicePv.wireToken());
        assertEquals("device-pv", devicePv.kind());
        assertEquals(0, devicePv.index());

        FoundationFieldbusPoint ff = FoundationFieldbusPoint.parse("ff:1");
        assertEquals("ff:1", ff.wireToken());
        assertEquals(1, ff.index());
    }

    @Test
    void readAndWriteGatewayPoints() throws Exception {
        gateway = new FakeFoundationFieldbusGateway();
        gateway.put("ai:1", 12.5);
        gateway.put("ao:2", 21.0);
        gateway.put("device:0:pv", 101.3);
        gateway.put("ff:1", 7.0);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new FoundationFieldbusDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "ai1", "ai:1",
                "ao2", "ao:2",
                "pv", "device:0:pv",
                "ff1", "ff:1"
        ));
        assertEquals(12.5, (Double) object.variables.get("ai1").firstRow().get("value"), 0.001);
        assertEquals(21.0, (Double) object.variables.get("ao2").firstRow().get("value"), 0.001);
        assertEquals(101.3, (Double) object.variables.get("pv").firstRow().get("value"), 0.001);
        assertEquals(7.0, (Double) object.variables.get("ff1").firstRow().get("value"), 0.001);

        driver.writePoint("ao2", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.25)
        ));
        assertEquals(33.25, gateway.get("ao:2"), 0.001);
        assertEquals(33.25, (Double) object.variables.get("ao2").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new FoundationFieldbusDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "ai:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeFoundationFieldbusGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ff-hse");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeFoundationFieldbusGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String token, double value) {
            values.put(normalize(token), value);
        }

        double get(String token) {
            return values.getOrDefault(normalize(token), 0.0);
        }

        void start() {
            executor.submit(this::acceptLoop);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    String line = readLine(in);
                    if (line == null) {
                        return;
                    }
                    String trimmed = line.trim();
                    String upper = trimmed.toUpperCase(Locale.ROOT);
                    if (upper.startsWith("RD ")) {
                        String token = normalize(trimmed.substring(3).trim());
                        Double value = values.get(token);
                        if (value == null) {
                            writeLine(out, "VALUE 0");
                        } else {
                            writeLine(out, "VALUE " + value);
                        }
                    } else if (upper.startsWith("WR ")) {
                        String rest = trimmed.substring(3).trim();
                        int space = rest.lastIndexOf(' ');
                        if (space < 0) {
                            writeLine(out, "ERR");
                            continue;
                        }
                        String token = normalize(rest.substring(0, space).trim());
                        double value = Double.parseDouble(rest.substring(space + 1).trim());
                        values.put(token, value);
                        writeLine(out, "OK");
                    } else {
                        writeLine(out, "ERR");
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private static String normalize(String token) {
            return token.trim().toLowerCase(Locale.ROOT).replace('=', ':');
        }

        private static void writeLine(OutputStream out, String line) throws IOException {
            out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            while (true) {
                int ch = in.read();
                if (ch < 0) {
                    if (buf.size() == 0) {
                        return null;
                    }
                    break;
                }
                if (ch == '\n') {
                    break;
                }
                if (ch != '\r') {
                    buf.write(ch);
                }
            }
            return buf.toString(StandardCharsets.US_ASCII);
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

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-foundation-fieldbus",
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
