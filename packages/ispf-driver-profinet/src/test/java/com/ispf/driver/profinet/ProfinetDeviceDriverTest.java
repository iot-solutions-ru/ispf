package com.ispf.driver.profinet;

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
 * Fake TCP loopback tests for the PROFINET PN IO gateway lab.
 * Certifies the lab dialect only — not PROFINET RT/IRT / DCP/RPC / PI stack.
 */
class ProfinetDeviceDriverTest {

    private ProfinetDeviceDriver driver;
    private FakeProfinetGateway gateway;

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
    void metadataIsProductionReadWritePnIoGatewayLab() {
        driver = new ProfinetDeviceDriver();
        assertEquals("profinet", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("34964", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("gateway") || description.contains("tcp")
                || description.contains("lab"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsSlotSubslotAndDeviceApiForms() throws Exception {
        ProfinetPoint slot = ProfinetPoint.parse("slot:1:subslot:1");
        assertEquals("slot:1:subslot:1", slot.wireToken());
        assertEquals(ProfinetPoint.Kind.SLOT_SUBSLOT, slot.kind());

        ProfinetPoint device = ProfinetPoint.parse("device:1:api:0:slot:1");
        assertEquals("device:1:api:0:slot:1", device.wireToken());
        assertEquals(ProfinetPoint.Kind.DEVICE_API_SLOT, device.kind());
    }

    @Test
    void readAndWriteGatewayIoPoints() throws Exception {
        gateway = new FakeProfinetGateway();
        gateway.put("slot:1:subslot:1", 12.5);
        gateway.put("device:1:api:0:slot:1", 21.0);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        TestDriverObject object = new TestDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new ProfinetDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "io", "slot:1:subslot:1",
                "dev", "device:1:api:0:slot:1"
        ));
        assertEquals(12.5, (Double) object.variables.get("io").firstRow().get("value"), 0.001);
        assertEquals(21.0, (Double) object.variables.get("dev").firstRow().get("value"), 0.001);

        driver.writePoint("io", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 33.25)
        ));
        assertEquals(33.25, gateway.get("slot:1:subslot:1"), 0.001);
        assertEquals(33.25, (Double) object.variables.get("io").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ProfinetDeviceDriver();
        driver.initialize(new TestDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "slot:1:subslot:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeProfinetGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-profinet");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeProfinetGateway() throws IOException {
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

    private static final class TestDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        TestDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-profinet",
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
