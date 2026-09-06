package com.ispf.driver.opchda;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP loopback tests for the OPC HDA HTTP/JSON gateway lab.
 * Certifies the lab dialect only — not OPC Classic HDA / DCOM.
 */
class OpcHdaDeviceDriverTest {

    private OpcHdaDeviceDriver driver;
    private FakeHdaGateway gateway;

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
    void metadataDescribesHdaGatewayLabNotClassicDcom() {
        driver = new OpcHdaDeviceDriver();
        assertEquals("opc-hda", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("48081", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("hda") && description.contains("lab"));
        assertTrue(description.contains("not") && (description.contains("dcom")
                || description.contains("classic")));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsItemAndTagForms() throws Exception {
        assertEquals("item", OpcHdaPoint.parse("item:Tag1").kindToken());
        assertEquals("Tag1", OpcHdaPoint.parse("item:Tag1").name());
        assertEquals("tag", OpcHdaPoint.parse("tag:Temperature").kindToken());
        assertEquals("Temperature", OpcHdaPoint.parse("tag:Temperature").name());
    }

    @Test
    void readLastValueAndInsertLabSample() throws Exception {
        gateway = new FakeHdaGateway();
        gateway.put("item", "Tag1", 21.5);
        gateway.put("tag", "Temperature", 18.25);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new OpcHdaDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "t1", "item:Tag1",
                "temp", "tag:Temperature"
        ));
        assertEquals(21.5, (Double) object.variables.get("t1").firstRow().get("value"), 0.001);
        assertEquals(18.25, (Double) object.variables.get("temp").firstRow().get("value"), 0.001);

        driver.writePoint("t1", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 22.5)
        ));
        assertEquals(22.5, gateway.get("item", "Tag1"), 0.001);
        assertEquals(22.5, (Double) object.variables.get("t1").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new OpcHdaDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "item:Tag1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeHdaGateway implements AutoCloseable {

        private static final Pattern OP = Pattern.compile("\"op\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern KIND = Pattern.compile("\"kind\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern VALUE = Pattern.compile(
                "\"value\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-opc-hda");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeHdaGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String kind, String name, double value) {
            values.put(key(kind, name), value);
        }

        double get(String kind, String name) {
            return values.getOrDefault(key(kind, name), 0.0);
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
                    writeLine(out, handleLine(line));
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private String handleLine(String line) {
            Matcher opMatcher = OP.matcher(line);
            Matcher kindMatcher = KIND.matcher(line);
            Matcher nameMatcher = NAME.matcher(line);
            if (!opMatcher.find() || !kindMatcher.find() || !nameMatcher.find()) {
                return "{\"ok\":false,\"error\":\"bad request\"}";
            }
            String op = opMatcher.group(1).toLowerCase(Locale.ROOT);
            String kind = kindMatcher.group(1);
            String name = nameMatcher.group(1);
            String k = key(kind, name);
            if ("get".equals(op)) {
                double value = values.getOrDefault(k, 0.0);
                return "{\"ok\":true,\"value\":" + format(value) + "}";
            }
            if ("set".equals(op)) {
                Matcher valueMatcher = VALUE.matcher(line);
                if (!valueMatcher.find()) {
                    return "{\"ok\":false,\"error\":\"missing value\"}";
                }
                double value = Double.parseDouble(valueMatcher.group(1));
                values.put(k, value);
                return "{\"ok\":true,\"value\":" + format(value) + "}";
            }
            return "{\"ok\":false,\"error\":\"unknown op\"}";
        }

        private static String key(String kind, String name) {
            return kind.toLowerCase(Locale.ROOT) + ":" + name;
        }

        private static String format(double value) {
            if (value == Math.rint(value)) {
                return Long.toString(Math.round(value));
            }
            return Double.toString(value);
        }

        private static void writeLine(OutputStream out, String line) throws IOException {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
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
            return buf.toString(StandardCharsets.UTF_8);
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
                    "test-opc-hda",
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
