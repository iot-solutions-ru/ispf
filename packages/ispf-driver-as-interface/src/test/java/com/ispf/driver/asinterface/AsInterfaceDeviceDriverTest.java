package com.ispf.driver.asinterface;

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
 * Fake TCP loopback tests for the AS-Interface gateway ASCII lab.
 * Certifies the lab dialect only — not AS-i physical master / yellow cable.
 */
class AsInterfaceDeviceDriverTest {

    private AsInterfaceDeviceDriver driver;
    private FakeAsInterfaceGateway gateway;

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
        driver = new AsInterfaceDeviceDriver();
        assertEquals("as-interface", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("tcp") || description.contains("gateway"));
        assertTrue(description.contains("not"));
    }

    @Test
    void pointParserAcceptsSlaveAndDiDoForms() throws Exception {
        AsInterfacePoint aggregate = AsInterfacePoint.parse("slave:3");
        assertEquals(3, aggregate.slave());
        assertEquals(AsInterfacePoint.Channel.AGGREGATE, aggregate.channel());

        AsInterfacePoint di = AsInterfacePoint.parse("slave:3:di0");
        assertEquals(AsInterfacePoint.Channel.DI, di.channel());
        assertEquals(0, di.bit());

        AsInterfacePoint dout = AsInterfacePoint.parse("slave:3:do1");
        assertEquals(AsInterfacePoint.Channel.DO, dout.channel());
        assertEquals(1, dout.bit());
    }

    @Test
    void readDiAndWriteDoLoopback() throws Exception {
        gateway = new FakeAsInterfaceGateway();
        gateway.put("slave:3:di0", 1);
        gateway.put("slave:3:do1", 0);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new AsInterfaceDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "di0", "slave:3:di0",
                "do1", "slave:3:do1"
        ));
        assertEquals(1.0, (Double) object.variables.get("di0").firstRow().get("value"), 0.001);
        assertEquals(0.0, (Double) object.variables.get("do1").firstRow().get("value"), 0.001);

        driver.writePoint("do1", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        ));
        assertEquals(1.0, gateway.get("slave:3:do1"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("do1").firstRow().get("value"), 0.001);
    }

    @Test
    void writeDiRejected() throws Exception {
        gateway = new FakeAsInterfaceGateway();
        gateway.put("slave:3:di0", 1);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new AsInterfaceDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("di0", "slave:3:di0"));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("di0", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                        Map.of("value", 0.0)
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("di"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new AsInterfaceDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "slave:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeAsInterfaceGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-as-interface");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeAsInterfaceGateway() throws IOException {
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
                    if (upper.startsWith("GET ") || upper.startsWith("RD ")) {
                        String token = normalize(trimmed.substring(trimmed.indexOf(' ') + 1).trim());
                        Double value = values.get(token);
                        if (value == null) {
                            writeLine(out, "ERR unknown");
                        } else {
                            writeLine(out, "VALUE " + Math.round(value));
                        }
                    } else if (upper.startsWith("SET ") || upper.startsWith("WR ")) {
                        String rest = trimmed.substring(trimmed.indexOf(' ') + 1).trim();
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
                    "test-as-interface",
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
