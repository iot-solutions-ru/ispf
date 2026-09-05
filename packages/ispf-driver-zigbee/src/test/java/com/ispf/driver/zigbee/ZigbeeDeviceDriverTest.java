package com.ispf.driver.zigbee;

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
 * Fake TCP loopback tests for the Zigbee ZCL coordinator gateway lab.
 * Certifies the lab dialect only — not 802.15.4 radio / NCP.
 */
class ZigbeeDeviceDriverTest {

    private static final String TEMP =
            "nwk:0x1234:ep:1:cluster:0x0402:attr:0";

    private ZigbeeDeviceDriver driver;
    private FakeZclGateway gateway;

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
    void metadataIsProductionZclGatewayLab() {
        driver = new ZigbeeDeviceDriver();
        assertEquals("zigbee", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("17754", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("zcl") || description.contains("gateway"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsNwkAttrAndIeee() throws Exception {
        ZigbeePoint attr = ZigbeePoint.parse(TEMP);
        assertEquals(ZigbeePoint.Kind.ZCL_ATTR, attr.kind());
        assertEquals(0x1234, attr.nwk());
        assertEquals(1, attr.endpoint());
        assertEquals(0x0402, attr.cluster());
        assertEquals(0, attr.attr());
        assertEquals("00124b0001234567", ZigbeePoint.parse("ieee:00124b0001234567").ieee());
    }

    @Test
    void readAttrAndIeeeWriteAttrLoopback() throws Exception {
        String wireTemp = ZigbeePoint.parse(TEMP).display();
        gateway = new FakeZclGateway();
        gateway.put(wireTemp, 25.5);
        gateway.put("ieee:00124b0001234567", 1);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new ZigbeeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "temp", TEMP,
                "addr", "ieee:00124b0001234567"
        ));
        assertEquals(25.5, (Double) object.variables.get("temp").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("addr").firstRow().get("value"), 0.001);

        driver.writePoint("temp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 21.0)
        ));
        assertEquals(21.0, gateway.value(wireTemp), 0.001);
        assertEquals(21.0, (Double) object.variables.get("temp").firstRow().get("value"), 0.001);
    }

    @Test
    void writeIeeeRejected() throws Exception {
        gateway = new FakeZclGateway();
        gateway.put("ieee:00124b0001234567", 1);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new ZigbeeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("addr", "ieee:00124b0001234567"));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("addr", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                        Map.of("value", 0.0)
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("ieee"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ZigbeeDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", TEMP)));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeZclGateway implements AutoCloseable {

        private static final Pattern OP = Pattern.compile("\"op\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern POINT = Pattern.compile("\"point\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*(-?[0-9.]+)");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-zigbee-zcl");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeZclGateway() throws IOException {
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
                    "test-zigbee",
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
