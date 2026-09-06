package com.ispf.driver.matter;

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
 * Fake TCP loopback tests for the Matter/CHIP controller gateway lab.
 * Certifies the lab dialect only — not full CSA Matter / CHIP SDK / Thread/BLE commissioning.
 */
class MatterDeviceDriverTest {

    private static final String ONOFF_ATTR =
            "node:1:ep:1:cluster:OnOff:attr:OnOff";
    private static final String CMD_ON = "node:1:cmd:On";

    private MatterDeviceDriver driver;
    private FakeMatterGateway gateway;

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
    void metadataIsProductionMatterControllerGatewayLab() {
        driver = new MatterDeviceDriver();
        assertEquals("matter", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("5540", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("matter") || description.contains("chip")
                || description.contains("gateway"));
        assertTrue(description.contains("lab") || description.contains("controller"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsAttrAndCmdForms() throws Exception {
        MatterPoint attr = MatterPoint.parse(ONOFF_ATTR);
        assertEquals(MatterPoint.Kind.ATTR, attr.kind());
        assertEquals(1, attr.node());
        assertEquals(1, attr.endpoint());
        assertEquals("OnOff", attr.cluster());
        assertEquals("OnOff", attr.attribute());
        assertTrue(attr.writable());

        MatterPoint cmd = MatterPoint.parse(CMD_ON);
        assertEquals(MatterPoint.Kind.CMD, cmd.kind());
        assertEquals(1, cmd.node());
        assertEquals("On", cmd.command());
        assertTrue(cmd.writable());
    }

    @Test
    void readAttrAndCmdWriteAttrAndCmdLoopback() throws Exception {
        gateway = new FakeMatterGateway();
        gateway.put(ONOFF_ATTR, 0);
        gateway.put(CMD_ON, 0);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new MatterDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "onoff", ONOFF_ATTR,
                "cmd", CMD_ON
        ));
        assertEquals(0.0, (Double) object.variables.get("onoff").firstRow().get("value"), 0.001);
        assertEquals(0.0, (Double) object.variables.get("cmd").firstRow().get("value"), 0.001);

        driver.writePoint("onoff", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        ));
        assertEquals(1.0, gateway.value(ONOFF_ATTR), 0.001);
        assertEquals(1.0, (Double) object.variables.get("onoff").firstRow().get("value"), 0.001);

        driver.writePoint("cmd", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 1.0)
        ));
        assertEquals(1.0, gateway.value(CMD_ON), 0.001);
        assertEquals(1.0, (Double) object.variables.get("cmd").firstRow().get("value"), 0.001);
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new MatterDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", ONOFF_ATTR)));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeMatterGateway implements AutoCloseable {

        private static final Pattern OP = Pattern.compile("\"op\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern POINT = Pattern.compile("\"point\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*(-?[0-9.]+)");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-matter");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeMatterGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String point, double value) {
            values.put(point.toLowerCase(Locale.ROOT), value);
        }

        double value(String point) {
            return values.getOrDefault(point.toLowerCase(Locale.ROOT), 0.0);
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
            Matcher pointMatcher = POINT.matcher(line);
            if (!opMatcher.find() || !pointMatcher.find()) {
                return "{\"ok\":false,\"error\":\"bad request\"}";
            }
            String op = opMatcher.group(1).toLowerCase(Locale.ROOT);
            String point = pointMatcher.group(1).toLowerCase(Locale.ROOT);
            if ("get".equals(op)) {
                double value = values.getOrDefault(point, 0.0);
                return "{\"ok\":true,\"value\":" + format(value) + "}";
            }
            if ("set".equals(op)) {
                Matcher valueMatcher = VALUE.matcher(line);
                if (!valueMatcher.find()) {
                    return "{\"ok\":false,\"error\":\"missing value\"}";
                }
                double value = Double.parseDouble(valueMatcher.group(1));
                values.put(point, value);
                return "{\"ok\":true,\"value\":" + format(value) + "}";
            }
            return "{\"ok\":false,\"error\":\"unknown op\"}";
        }

        private static String format(double value) {
            if (value == Math.rint(value)) {
                return Long.toString(Math.round(value));
            }
            return Double.toString(value);
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
                    "test-matter",
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
