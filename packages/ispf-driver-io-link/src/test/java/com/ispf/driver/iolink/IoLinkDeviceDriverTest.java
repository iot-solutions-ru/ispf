package com.ispf.driver.iolink;

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
 * Fake TCP loopback tests for the IO-Link master JSON-over-TCP lab bridge.
 * Certifies the lab dialect only — not IO-Link PHY / ISDU.
 */
class IoLinkDeviceDriverTest {

    private IoLinkDeviceDriver driver;
    private FakeIoLinkBridge bridge;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
    }

    @Test
    void metadataIsProductionReadWriteLabBridge() {
        driver = new IoLinkDeviceDriver();
        assertEquals("io-link", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab") || description.contains("json"));
        assertTrue(description.contains("not"));
    }

    @Test
    void pointParserAcceptsPortAndProcessDataForms() throws Exception {
        assertEquals(1, IoLinkPoint.parse("port:1").port());
        assertEquals(IoLinkPoint.Channel.PORT, IoLinkPoint.parse("port:1").channel());
        assertEquals(IoLinkPoint.Channel.PDIN, IoLinkPoint.parse("port:1:pdin").channel());
        assertEquals(IoLinkPoint.Channel.PDOUT, IoLinkPoint.parse("port:1:pdout").channel());
    }

    @Test
    void readPdinAndWritePdoutLoopback() throws Exception {
        bridge = new FakeIoLinkBridge();
        bridge.putPdin(1, 17);
        bridge.putPdout(1, 0);
        bridge.start();
        assertTrue(bridge.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(bridge.port()),
                "timeoutMs", "2000"
        ));
        driver = new IoLinkDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "in", "port:1:pdin",
                "out", "port:1:pdout"
        ));
        assertEquals(17.0, (Double) object.variables.get("in").firstRow().get("value"), 0.001);
        assertEquals(0.0, (Double) object.variables.get("out").firstRow().get("value"), 0.001);

        driver.writePoint("out", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.0)
        ));
        assertEquals(42.0, bridge.pdout(1), 0.001);
        assertEquals(42.0, (Double) object.variables.get("out").firstRow().get("value"), 0.001);
    }

    @Test
    void writePdinRejected() throws Exception {
        bridge = new FakeIoLinkBridge();
        bridge.putPdin(1, 1);
        bridge.start();
        assertTrue(bridge.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(bridge.port()),
                "timeoutMs", "2000"
        ));
        driver = new IoLinkDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("in", "port:1:pdin"));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("in", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                        Map.of("value", 9.0)
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("pdin"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new IoLinkDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "port:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeIoLinkBridge implements AutoCloseable {

        private static final Pattern PORT = Pattern.compile("\"port\"\\s*:\\s*(\\d+)");
        private static final Pattern CHANNEL = Pattern.compile("\"channel\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern PDOUT = Pattern.compile("\"pdout\"\\s*:\\s*(-?[0-9.]+)");
        private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*(-?[0-9.]+)");
        private static final Pattern OP = Pattern.compile("\"op\"\\s*:\\s*\"([^\"]+)\"");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-io-link");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Double> pdin = new ConcurrentHashMap<>();
        private final Map<Integer, Double> pdout = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeIoLinkBridge() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putPdin(int port, double value) {
            pdin.put(port, value);
        }

        void putPdout(int port, double value) {
            pdout.put(port, value);
        }

        double pdout(int port) {
            return pdout.getOrDefault(port, 0.0);
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
            Matcher portMatcher = PORT.matcher(line);
            if (!opMatcher.find() || !portMatcher.find()) {
                return "{\"ok\":false,\"error\":\"bad request\"}";
            }
            String op = opMatcher.group(1).toLowerCase(Locale.ROOT);
            int port = Integer.parseInt(portMatcher.group(1));
            String channel = "pdin";
            Matcher channelMatcher = CHANNEL.matcher(line);
            if (channelMatcher.find()) {
                channel = channelMatcher.group(1).toLowerCase(Locale.ROOT);
            }
            if ("get".equals(op)) {
                double value = "pdout".equals(channel)
                        ? pdout.getOrDefault(port, 0.0)
                        : pdin.getOrDefault(port, 0.0);
                return "{\"ok\":true,\"value\":" + Math.round(value) + "}";
            }
            if ("set".equals(op)) {
                Double value = null;
                Matcher pdoutMatcher = PDOUT.matcher(line);
                if (pdoutMatcher.find()) {
                    value = Double.parseDouble(pdoutMatcher.group(1));
                } else {
                    Matcher valueMatcher = VALUE.matcher(line);
                    if (valueMatcher.find()) {
                        value = Double.parseDouble(valueMatcher.group(1));
                    }
                }
                if (value == null) {
                    return "{\"ok\":false,\"error\":\"missing pdout\"}";
                }
                pdout.put(port, value);
                return "{\"ok\":true,\"value\":" + Math.round(value) + "}";
            }
            return "{\"ok\":false,\"error\":\"unknown op\"}";
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
                    "test-io-link",
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
