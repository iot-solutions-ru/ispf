package com.ispf.driver.cclinkie;

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
 * Fake TCP loopback tests for the CC-Link IE Field gateway lab.
 * Certifies the lab dialect only — not CC-Link RS-485 / IE Field ASIC / CLPA stack.
 */
class CcLinkIeDeviceDriverTest {

    private CcLinkIeDeviceDriver driver;
    private FakeCcLinkIeGateway gateway;

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
    void metadataIsProductionReadWriteIeFieldGatewayLab() {
        driver = new CcLinkIeDeviceDriver();
        assertEquals("cc-link-ie", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("5007", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("ie") || description.contains("gateway")
                || description.contains("tcp"));
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsDRWAndDevForms() throws Exception {
        assertEquals("D100", CcLinkIePoint.parse("D100").wireToken());
        assertEquals("R0", CcLinkIePoint.parse("R0").wireToken());
        assertEquals("W0", CcLinkIePoint.parse("W0").wireToken());
        assertEquals("D100", CcLinkIePoint.parse("dev:D100").wireToken());
    }

    @Test
    void readAndWriteGatewayRegisters() throws Exception {
        gateway = new FakeCcLinkIeGateway();
        gateway.put("D100", 44.5);
        gateway.put("R0", 2.0);
        gateway.put("W0", 9.75);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new CcLinkIeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "d100", "D100",
                "r0", "R0",
                "w0", "W0",
                "dev", "dev:D100"
        ));
        assertEquals(44.5, (Double) object.variables.get("d100").firstRow().get("value"), 0.001);
        assertEquals(2.0, (Double) object.variables.get("r0").firstRow().get("value"), 0.001);
        assertEquals(9.75, (Double) object.variables.get("w0").firstRow().get("value"), 0.001);
        assertEquals(44.5, (Double) object.variables.get("dev").firstRow().get("value"), 0.001);

        driver.writePoint("w0", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 11.5)
        ));
        assertEquals(11.5, gateway.get("W0"), 0.001);
        assertEquals(11.5, (Double) object.variables.get("w0").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new CcLinkIeDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "D100")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeCcLinkIeGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-cc-link-ie");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeCcLinkIeGateway() throws IOException {
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
            return token.trim().toUpperCase(Locale.ROOT).replace("DEV:", "");
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
                    "test-cc-link-ie",
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
