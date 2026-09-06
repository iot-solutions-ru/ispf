package com.ispf.driver.opcae;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP loopback tests for the OPC A&amp;E HTTP/JSON gateway lab.
 * Certifies the lab dialect only — not OPC Classic DCOM / COM A&amp;E.
 */
class OpcAeDeviceDriverTest {

    private OpcAeDeviceDriver driver;
    private FakeAeGateway gateway;

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
    void metadataDescribesHttpJsonGatewayLabNotDcom() {
        driver = new OpcAeDeviceDriver();
        assertEquals("opc-ae", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("48080", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("http/json"));
        assertTrue(description.contains("a&e") || description.contains("gateway"));
        assertTrue(description.contains("not") && description.contains("dcom"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsAlarmSourceAreaForms() throws Exception {
        assertEquals("alarm", OpcAePoint.parse("alarm:1").kindToken());
        assertEquals("1", OpcAePoint.parse("alarm:1").id());
        assertEquals("source", OpcAePoint.parse("source:Tank1").kindToken());
        assertEquals("Tank1", OpcAePoint.parse("source:Tank1").id());
        assertEquals("area", OpcAePoint.parse("area:Plant").kindToken());
        assertEquals("Plant", OpcAePoint.parse("area:Plant").id());
    }

    @Test
    void readActiveAlarmsAndAcknowledgeLoopback() throws Exception {
        gateway = new FakeAeGateway();
        gateway.put("alarm", "1", 2.0, "High Level");
        gateway.put("source", "Tank1", 1.0, "Tank1 active");
        gateway.put("area", "Plant", 3.0, "Plant area");
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new OpcAeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "a1", "alarm:1",
                "src", "source:Tank1",
                "area", "area:Plant"
        ));
        assertEquals(2.0, (Double) object.variables.get("a1").firstRow().get("value"), 0.001);
        assertEquals("High Level", object.variables.get("a1").firstRow().get("text"));
        assertEquals(1.0, (Double) object.variables.get("src").firstRow().get("value"), 0.001);
        assertEquals(3.0, (Double) object.variables.get("area").firstRow().get("value"), 0.001);

        driver.writePoint("a1", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "ack")
        ));
        assertEquals(1, gateway.ackCount());
        assertEquals(0.0, (Double) object.variables.get("a1").firstRow().get("value"), 0.001);

        driver.writePoint("a1", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        ));
        assertEquals(1.0, gateway.enabled("alarm", "1"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new OpcAeDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "alarm:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeAeGateway implements AutoCloseable {

        private static final Pattern OP = Pattern.compile("\"op\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern KIND = Pattern.compile("\"kind\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern ENABLED = Pattern.compile("\"enabled\"\\s*:\\s*(-?[0-9.]+)");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-opc-ae");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Sample> samples = new ConcurrentHashMap<>();
        private final Map<String, Double> enabled = new ConcurrentHashMap<>();
        private final AtomicInteger ackCount = new AtomicInteger();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeAeGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String kind, String id, double value, String text) {
            samples.put(key(kind, id), new Sample(value, text));
        }

        int ackCount() {
            return ackCount.get();
        }

        double enabled(String kind, String id) {
            return enabled.getOrDefault(key(kind, id), 0.0);
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
            Matcher idMatcher = ID.matcher(line);
            if (!opMatcher.find() || !kindMatcher.find() || !idMatcher.find()) {
                return "{\"ok\":false,\"error\":\"bad request\"}";
            }
            String op = opMatcher.group(1).toLowerCase(Locale.ROOT);
            String kind = kindMatcher.group(1);
            String id = idMatcher.group(1);
            String k = key(kind, id);
            if ("get".equals(op)) {
                Sample sample = samples.getOrDefault(k, new Sample(0.0, ""));
                return "{\"ok\":true,\"value\":" + format(sample.value)
                        + ",\"text\":\"" + sample.text.replace("\"", "\\\"") + "\"}";
            }
            if ("ack".equals(op)) {
                ackCount.incrementAndGet();
                samples.put(k, new Sample(0.0, "acknowledged"));
                return "{\"ok\":true,\"value\":0,\"text\":\"acknowledged\"}";
            }
            if ("set".equals(op)) {
                Matcher enabledMatcher = ENABLED.matcher(line);
                if (!enabledMatcher.find()) {
                    return "{\"ok\":false,\"error\":\"missing enabled\"}";
                }
                double value = Double.parseDouble(enabledMatcher.group(1));
                enabled.put(k, value);
                samples.put(k, new Sample(value, "enabled"));
                return "{\"ok\":true,\"value\":" + format(value) + ",\"text\":\"enabled\"}";
            }
            return "{\"ok\":false,\"error\":\"unknown op\"}";
        }

        private static String key(String kind, String id) {
            return kind.toLowerCase(Locale.ROOT) + ":" + id;
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

        private record Sample(double value, String text) {
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
                    "test-opc-ae",
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
