package com.ispf.driver.hartserial;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.hartserial.codec.HartSerialCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process ServerSocket peer tests for the HART serial-gateway driver.
 */
class HartSerialDeviceDriverTest {

    private HartSerialDeviceDriver driver;
    private FakeHartSerialGateway server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void metadataDescribesGatewayNotLab() {
        driver = new HartSerialDeviceDriver();
        assertEquals("hart-serial", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertFalse(description.contains("lab"));
        assertFalse(description.contains("stub"));
        assertFalse(description.contains("placeholder"));
        assertTrue(description.contains("not fsk") || description.contains("not a full"));
    }

    @Test
    void pointParserAcceptsPvCmdAndDeviceForms() throws Exception {
        assertEquals(new HartSerialPoint(0, 1), HartSerialPoint.parse("pv"));
        assertEquals(new HartSerialPoint(0, 1), HartSerialPoint.parse("cmd:1"));
        assertEquals(new HartSerialPoint(0, 3), HartSerialPoint.parse("cmd:3"));
        assertEquals(new HartSerialPoint(0, 1), HartSerialPoint.parse("device:0"));
        assertEquals(new HartSerialPoint(2, 1), HartSerialPoint.parse("device:2:cmd:1"));
        assertEquals(new HartSerialPoint(0, 1), HartSerialPoint.parse("0:1"));
    }

    @Test
    void gatewayPassThroughReadPv() throws Exception {
        server = new FakeHartSerialGateway();
        server.put(0, 21.5f);
        server.start();
        assertTrue(server.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new HartSerialDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "pv", "pv",
                "cmd1", "cmd:1",
                "dev0", "device:0",
                "dev0cmd1", "device:0:cmd:1"
        ));
        assertEquals(21.5, (Double) object.variables.get("pv").firstRow().get("value"), 0.001);
        assertEquals(21.5, (Double) object.variables.get("cmd1").firstRow().get("value"), 0.001);
        assertEquals(21.5, (Double) object.variables.get("dev0").firstRow().get("value"), 0.001);
        assertEquals(21.5, (Double) object.variables.get("dev0cmd1").firstRow().get("value"), 0.001);
        assertEquals(1L, object.variables.get("pv").firstRow().get("command"));
    }

    @Test
    void command3ReadsDynamicStylePv() throws Exception {
        server = new FakeHartSerialGateway();
        server.put(0, 33.25f);
        server.start();
        assertTrue(server.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new HartSerialDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("dyn", "cmd:3"));
        assertEquals(33.25, (Double) object.variables.get("dyn").firstRow().get("value"), 0.001);
        assertEquals(3L, object.variables.get("dyn").firstRow().get("command"));
    }

    @Test
    void writeIsRejected() {
        driver = new HartSerialDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("pv", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.STRING).build(),
                        Map.of("value", "x")
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("rejects writes"));
    }

    private static final class FakeHartSerialGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-hart-serial");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Float> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        FakeHartSerialGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int device, float pv) {
            values.put(device, pv);
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
                    byte[] pdu = HartSerialCodec.readHartPdu(in);
                    HartSerialCodec.HartCommand command = HartSerialCodec.parseHartCommand(pdu);
                    float pv = values.getOrDefault(command.address(), 0f);
                    out.write(HartSerialCodec.encodeResponse(command.address(), command.command(), pv));
                    out.flush();
                }
            } catch (IOException ignored) {
                // closed
            }
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
                    "test-hart-serial", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
