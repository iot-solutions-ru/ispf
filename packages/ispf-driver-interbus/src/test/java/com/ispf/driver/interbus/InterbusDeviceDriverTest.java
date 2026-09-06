package com.ispf.driver.interbus;

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
 * Fake TCP loopback tests for the INTERBUS gateway process-data lab.
 * Certifies the lab dialect only — not Phoenix Interbus master ASIC / not Modbus.
 */
class InterbusDeviceDriverTest {

    private InterbusDeviceDriver driver;
    private FakeInterbusGateway gateway;

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
    void metadataIsProductionReadWriteTcpGatewayLab() {
        driver = new InterbusDeviceDriver();
        assertEquals("interbus", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("tcp") || description.contains("gateway") || description.contains("lab"));
        assertTrue(description.contains("not"));
    }

    @Test
    void pointParserAcceptsSlotWordForms() throws Exception {
        InterbusPoint slot = InterbusPoint.parse("slot:1");
        assertEquals(1, slot.slot());
        assertEquals(0, slot.word());

        InterbusPoint word = InterbusPoint.parse("word:0");
        assertEquals(0, word.slot());
        assertEquals(0, word.word());

        InterbusPoint both = InterbusPoint.parse("slot:1:word:0");
        assertEquals(1, both.slot());
        assertEquals(0, both.word());
    }

    @Test
    void readAndWriteProcessImageWord() throws Exception {
        gateway = new FakeInterbusGateway();
        gateway.put("slot:1:word:0", 100);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new InterbusDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("w0", "slot:1:word:0"));
        assertEquals(100.0, (Double) object.variables.get("w0").firstRow().get("value"), 0.001);

        driver.writePoint("w0", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 0xABCD)
        ));
        assertEquals(0xABCD, gateway.get("slot:1:word:0"), 0.001);
        assertEquals((double) 0xABCD, (Double) object.variables.get("w0").firstRow().get("value"), 0.001);

        driver.readPoints(Map.of(
                "slotOnly", "slot:1",
                "wordOnly", "word:0"
        ));
        assertEquals(0xABCD, (Double) object.variables.get("slotOnly").firstRow().get("value"), 0.001);
        assertEquals(0.0, (Double) object.variables.get("wordOnly").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new InterbusDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "slot:1:word:0")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeInterbusGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-interbus");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeInterbusGateway() throws IOException {
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
                            writeLine(out, "VALUE " + Math.round(value));
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
            String t = token.trim().toLowerCase(Locale.ROOT).replace('=', ':');
            if (t.matches("slot:\\d+")) {
                return t + ":word:0";
            }
            if (t.matches("word:\\d+")) {
                return "slot:0:" + t;
            }
            return t;
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
                    "test-interbus",
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
