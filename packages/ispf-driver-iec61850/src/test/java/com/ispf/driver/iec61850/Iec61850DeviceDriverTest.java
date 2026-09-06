package com.ispf.driver.iec61850;

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
 * Fake TCP loopback tests for the IEC 61850 MMS client lab subset.
 * Certifies the lab dialect only — not a full IEC 61850 / SCL / 61850-8-1 stack.
 */
class Iec61850DeviceDriverTest {

    private Iec61850DeviceDriver driver;
    private FakeMmsLab lab;

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
    void metadataIsProductionReadWriteMmsLab() {
        driver = new Iec61850DeviceDriver();
        assertEquals("iec61850", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("102", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("mms"));
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsObjectReferenceForms() throws Exception {
        assertEquals("LD0/MMXU1.TotW.mag.f",
                Iec61850Point.parse("LD0/MMXU1.TotW.mag.f").wireToken());
        assertEquals("IED1/LLN0.Mod.stVal",
                Iec61850Point.parse("IED1/LLN0.Mod.stVal").wireToken());
        assertEquals("analog", Iec61850Point.parse("LD0/MMXU1.TotW.mag.f").kind());
        assertEquals("status", Iec61850Point.parse("IED1/LLN0.Mod.stVal").kind());
    }

    @Test
    void readAndWriteMmsLabPoints() throws Exception {
        lab = new FakeMmsLab();
        lab.put("LD0/MMXU1.TotW.mag.f", 12.5);
        lab.put("IED1/LLN0.Mod.stVal", 1.0);
        lab.start();
        assertTrue(lab.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(lab.port()),
                "timeoutMs", "2000"
        ));
        driver = new Iec61850DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "totw", "LD0/MMXU1.TotW.mag.f",
                "mod", "IED1/LLN0.Mod.stVal"
        ));
        assertEquals(12.5, (Double) object.variables.get("totw").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("mod").firstRow().get("value"), 0.001);
        assertEquals("good", object.variables.get("totw").firstRow().get("quality"));

        driver.writePoint("mod", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 2.0)
        ));
        assertEquals(2.0, lab.get("IED1/LLN0.Mod.stVal"), 0.001);
        assertEquals(2.0, (Double) object.variables.get("mod").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new Iec61850DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "LD0/MMXU1.TotW.mag.f")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeMmsLab implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-iec61850-mms");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeMmsLab() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String token, double value) {
            values.put(token, value);
        }

        double get(String token) {
            return values.getOrDefault(token, 0.0);
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
                        String token = trimmed.substring(3).trim();
                        Double value = values.get(token);
                        writeLine(out, "VALUE " + (value == null ? 0.0 : value));
                    } else if (upper.startsWith("WR ")) {
                        String rest = trimmed.substring(3).trim();
                        int space = rest.lastIndexOf(' ');
                        if (space < 0) {
                            writeLine(out, "ERR");
                            continue;
                        }
                        String token = rest.substring(0, space).trim();
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
                    "test-iec61850",
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
