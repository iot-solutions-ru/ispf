package com.ispf.driver.wisun;

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
 * Fake TCP CoAP border-router loopback tests for the Wi-SUN lab.
 * Certifies lab dialect only — not Wi-SUN FAN PHY / FAN stack.
 */
class WisunDeviceDriverTest {

    private WisunDeviceDriver driver;
    private FakeWisunBorderRouter gateway;

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
    void metadataDescribesBorderRouterCoapLabNotFanPhy() {
        driver = new WisunDeviceDriver();
        assertEquals("wisun", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("5683", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("coap") || description.contains("border"));
        assertTrue(description.contains("not") && (description.contains("fan") || description.contains("phy")));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsNodeAndPathForms() throws Exception {
        assertEquals("/nodes/1/value", WisunPoint.parse("node:1").path());
        assertEquals("/nodes/1/value", WisunPoint.parse("/nodes/1/value").path());
        assertEquals("/nodes/2/value", WisunPoint.parse("coap:/nodes/2/value").path());
    }

    @Test
    void getAndPutLoopback() throws Exception {
        gateway = new FakeWisunBorderRouter();
        gateway.put("/nodes/1/value", 18.25f);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new WisunDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("n1", "node:1"));
        assertEquals(18.25, (Double) object.variables.get("n1").firstRow().get("value"), 0.001);
        assertEquals("/nodes/1/value", object.variables.get("n1").firstRow().get("path"));

        driver.writePoint("n1", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 27.5)
        ));
        assertEquals(27.5f, gateway.get("/nodes/1/value"), 0.001f);
        assertTrue(gateway.writeLatchAwait(2, TimeUnit.SECONDS));
    }

    private static final class FakeWisunBorderRouter implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-wisun");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, Float> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch writeSeen = new CountDownLatch(1);

        FakeWisunBorderRouter() throws IOException {
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
                        writeLine(out, "2.05 Content " + value);
                    } else if (upper.startsWith("PUT ")) {
                        String rest = trimmed.substring(4).trim();
                        int space = rest.lastIndexOf(' ');
                        String path = space < 0 ? rest : rest.substring(0, space).trim();
                        float value = space < 0 ? 0f : Float.parseFloat(rest.substring(space + 1).trim());
                        values.put(path, value);
                        writeSeen.countDown();
                        writeLine(out, "2.04 Changed");
                    } else {
                        writeLine(out, "4.00 Bad Request");
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
            return new PlatformObject("test-wisun", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
