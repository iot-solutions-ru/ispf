package com.ispf.driver.fanucfocas;

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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
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
 * Fake TCP loopback tests for the Fanuc FOCAS-shaped CNC gateway lab.
 * Certifies the lab dialect only — not Fanuc FOCAS library / proprietary SDK / real CNC.
 */
class FanucFocasDeviceDriverTest {

    private FanucFocasDeviceDriver driver;
    private FakeFocasGateway gateway;

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
    void metadataIsProductionFocasGatewayLab() {
        driver = new FanucFocasDeviceDriver();
        assertEquals("fanuc-focas", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("8193", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("focas") || description.contains("gateway"));
        assertTrue(description.contains("lab") || description.contains("tcp"));
        assertTrue(description.contains("not"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsPmcCncAbsAndStatRun() throws Exception {
        FanucFocasPoint pmc = FanucFocasPoint.parse("pmc:D0001");
        assertEquals(FanucFocasPoint.Kind.PMC, pmc.kind());
        assertEquals("pmc:D0001", pmc.display());
        assertTrue(pmc.writable());

        FanucFocasPoint abs = FanucFocasPoint.parse("cnc:abs:1");
        assertEquals(FanucFocasPoint.Kind.CNC_ABS, abs.kind());
        assertEquals(1, abs.axis());
        assertTrue(!abs.writable());

        FanucFocasPoint run = FanucFocasPoint.parse("stat:run");
        assertEquals(FanucFocasPoint.Kind.STAT_RUN, run.kind());
        assertTrue(!run.writable());
    }

    @Test
    void readPmcCncStatAndWritePmcLoopback() throws Exception {
        gateway = new FakeFocasGateway();
        gateway.put("pmc:D0001", 100);
        gateway.put("cnc:abs:1", 12.5);
        gateway.put("stat:run", 1);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new FanucFocasDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "pmc", "pmc:D0001",
                "abs", "cnc:abs:1",
                "run", "stat:run"
        ));
        assertEquals(100.0, (Double) object.variables.get("pmc").firstRow().get("value"), 0.001);
        assertEquals(12.5, (Double) object.variables.get("abs").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("run").firstRow().get("value"), 0.001);

        driver.writePoint("pmc", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 250.0)
        ));
        assertEquals(250.0, gateway.value("pmc:D0001"), 0.001);
        assertEquals(250.0, (Double) object.variables.get("pmc").firstRow().get("value"), 0.001);
    }

    @Test
    void writeNonPmcRejected() throws Exception {
        gateway = new FakeFocasGateway();
        gateway.put("stat:run", 1);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new FanucFocasDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("run", "stat:run"));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("run", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                        Map.of("value", 0.0)
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("pmc")
                || error.getMessage().toLowerCase(Locale.ROOT).contains("stat"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new FanucFocasDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "pmc:D0001")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeFocasGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-fanuc-focas");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeFocasGateway() throws IOException {
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
                    String payload = readFrame(in);
                    if (payload == null) {
                        return;
                    }
                    writeFrame(out, handlePayload(payload));
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private String handlePayload(String payload) {
            String trimmed = payload.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("GET ")) {
                String point = trimmed.substring(4).trim().toLowerCase(Locale.ROOT);
                double value = values.getOrDefault(point, 0.0);
                return "OK " + format(value);
            }
            if (upper.startsWith("SET ")) {
                String rest = trimmed.substring(4).trim();
                int space = rest.lastIndexOf(' ');
                if (space <= 0) {
                    return "ERR missing value";
                }
                String point = rest.substring(0, space).trim().toLowerCase(Locale.ROOT);
                double value = Double.parseDouble(rest.substring(space + 1).trim());
                values.put(point, value);
                return "OK " + format(value);
            }
            return "ERR unknown op";
        }

        private static String format(double value) {
            if (value == Math.rint(value)) {
                return Long.toString(Math.round(value));
            }
            return Double.toString(value);
        }

        private static void writeFrame(OutputStream out, String payload) throws IOException {
            byte[] body = payload.getBytes(StandardCharsets.US_ASCII);
            byte[] header = ByteBuffer.allocate(2).putShort((short) body.length).array();
            out.write(header);
            out.write(body);
            out.flush();
        }

        private static String readFrame(InputStream in) throws IOException {
            byte[] header = readFully(in, 2);
            if (header == null) {
                return null;
            }
            int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
            if (length == 0) {
                return "";
            }
            byte[] body = readFully(in, length);
            if (body == null) {
                throw new EOFException("EOF body");
            }
            return new String(body, StandardCharsets.US_ASCII);
        }

        private static byte[] readFully(InputStream in, int length) throws IOException {
            byte[] buf = new byte[length];
            int offset = 0;
            while (offset < length) {
                int n = in.read(buf, offset, length - offset);
                if (n < 0) {
                    if (offset == 0) {
                        return null;
                    }
                    throw new EOFException("EOF");
                }
                offset += n;
            }
            return buf;
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
                    "test-fanuc-focas",
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
