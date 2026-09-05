package com.ispf.driver.isa100;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP gateway loopback tests for the ISA100 ASCII/JSON lab.
 * Certifies lab dialect only — not ISA100.11a RF / WCI stack.
 */
class Isa100DeviceDriverTest {

    private Isa100DeviceDriver driver;
    private FakeIsa100Gateway gateway;

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
    void metadataDescribesGatewayLabNotIsa100Rf() {
        driver = new Isa100DeviceDriver();
        assertEquals("isa100", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("4840", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab") || description.contains("gateway"));
        assertTrue(description.contains("not") && (description.contains("isa100.11a")
                || description.contains("wireless compliance") || description.contains("rf")));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsPvTagAndDeviceForms() throws Exception {
        assertEquals("/devices/1/pv", Isa100Point.parse("pv").path());
        assertEquals("/tags/FI-101", Isa100Point.parse("tag:FI-101").path());
        assertEquals("/devices/2/pv", Isa100Point.parse("device:2/pv").path());
        assertEquals("/devices/1/pv", Isa100Point.parse("/devices/1/pv").path());
    }

    @Test
    void getAndSetJsonLoopback() throws Exception {
        gateway = new FakeIsa100Gateway();
        gateway.put("/devices/1/pv", 55.5f);
        gateway.put("/tags/FI-101", 12.0f);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new Isa100DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "pv", "pv",
                "tag", "tag:FI-101"
        ));
        assertEquals(55.5, (Double) object.variables.get("pv").firstRow().get("value"), 0.001);
        assertEquals("/devices/1/pv", object.variables.get("pv").firstRow().get("path"));
        assertEquals(12.0, (Double) object.variables.get("tag").firstRow().get("value"), 0.001);

        driver.writePoint("pv", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 66.0)
        ));
        assertEquals(66.0f, gateway.get("/devices/1/pv"), 0.001f);
        assertTrue(gateway.writeLatchAwait(2, TimeUnit.SECONDS));
    }

    private static final class FakeIsa100Gateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-isa100");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, Float> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch writeSeen = new CountDownLatch(1);

        FakeIsa100Gateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String path, float value) {
            values.put(path, value);
        }

        float get(String path) {
            return values.getOrDefault(path, 0f);
        }

        void start() {
            executor.submit(this::acceptLoop);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        boolean writeLatchAwait(long timeout, TimeUnit unit) throws InterruptedException {
            return writeSeen.await(timeout, unit);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    return;
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
                    if (upper.startsWith("GET ")) {
                        String path = trimmed.substring(4).trim();
                        float value = values.getOrDefault(path, 0f);
                        writeLine(out, "{\"path\":\"" + path + "\",\"value\":" + value + "}");
                    } else if (upper.startsWith("SET ")) {
                        String rest = trimmed.substring(4).trim();
                        int space = rest.lastIndexOf(' ');
                        String path = space < 0 ? rest : rest.substring(0, space).trim();
                        float value = space < 0 ? 0f : Float.parseFloat(rest.substring(space + 1).trim());
                        values.put(path, value);
                        writeSeen.countDown();
                        writeLine(out, "{\"ok\":true}");
                    } else {
                        writeLine(out, "ERR");
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static void writeLine(OutputStream out, String line) throws IOException {
            out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0) {
                    if (buf.size() == 0) {
                        return null;
                    }
                    break;
                }
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    buf.write(b);
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
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-isa100", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
