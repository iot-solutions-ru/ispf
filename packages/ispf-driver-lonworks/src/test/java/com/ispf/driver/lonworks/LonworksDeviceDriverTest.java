package com.ispf.driver.lonworks;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.lonworks.codec.LonworksLabCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
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
 * Fake TCP loopback tests for the LonWorks LonTalk-IP gateway lab codec.
 */
class LonworksDeviceDriverTest {

    private LonworksDeviceDriver driver;
    private FakeLonworksGateway gateway;

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
    void metadataDescribesGatewayLabNotNativeLontalk() {
        driver = new LonworksDeviceDriver();
        assertEquals("lonworks", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab") || description.contains("gateway"));
        assertTrue(description.contains("not native") || description.contains("not echelon"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsNviNvoAndIndexForms() throws Exception {
        assertEquals("nviTemp", LonworksPoint.parse("nviTemp").gatewayToken());
        assertEquals("nvoSetpoint", LonworksPoint.parse("nvoSetpoint").gatewayToken());
        assertEquals("nvi:temp", LonworksPoint.parse("nvi:temp").gatewayToken());
        assertEquals("nv:1", LonworksPoint.parse("nv:1").gatewayToken());
    }

    @Test
    void readAndWriteNetworkVariables() throws Exception {
        gateway = new FakeLonworksGateway();
        gateway.put("nviTemp", 21.5f);
        gateway.put("nvoSetpoint", 20.0f);
        gateway.put("nv:1", 1.25f);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new LonworksDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "temp", "nviTemp",
                "sp", "nvoSetpoint",
                "nv1", "nv:1"
        ));
        assertEquals(21.5, (Double) object.variables.get("temp").firstRow().get("value"), 0.001);
        assertEquals(20.0, (Double) object.variables.get("sp").firstRow().get("value"), 0.001);
        assertEquals(1.25, (Double) object.variables.get("nv1").firstRow().get("value"), 0.001);

        driver.writePoint("sp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 22.5)
        ));
        assertEquals(22.5f, gateway.get("nvoSetpoint"), 0.001f);
        assertEquals(22.5, (Double) object.variables.get("sp").firstRow().get("value"), 0.001);
    }

    private static final class FakeLonworksGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-lonworks");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, Float> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeLonworksGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String nv, float value) {
            values.put(nv, value);
        }

        float get(String nv) {
            return values.getOrDefault(nv, 0f);
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
                    LonworksLabCodec.Request request = LonworksLabCodec.parseRequest(line);
                    if (request instanceof LonworksLabCodec.GetRequest get) {
                        float value = values.getOrDefault(get.nvToken(), 0f);
                        out.write(("OK " + Float.toString(value) + "\n").getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } else if (request instanceof LonworksLabCodec.SetRequest set) {
                        values.put(set.nvToken(), set.value());
                        out.write("OK\n".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(64);
            while (true) {
                int b = in.read();
                if (b < 0) {
                    if (buffer.size() == 0) {
                        throw new EOFException();
                    }
                    break;
                }
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    buffer.write(b);
                }
            }
            return buffer.toString(StandardCharsets.US_ASCII);
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
            return new PlatformObject(
                    "test-lonworks", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
