package com.ispf.driver.eebus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.eebus.codec.EebusLabCodec;
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
 * Fake TCP loopback tests for the EEBus SHIP/SPINE-lite TCP lab codec.
 * Certifies the lab dialect only — not full EEBus/SHIP.
 */
class EebusDeviceDriverTest {

    private EebusDeviceDriver driver;
    private FakeEebusSpineLab gateway;

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
    void metadataDescribesTcpSpineLiteLabNotFullEebus() {
        driver = new EebusDeviceDriver();
        assertEquals("eebus", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertEquals("4712", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("spine") || description.contains("lab"));
        assertTrue(description.contains("not full") || description.contains("not official"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsPowerSetpointAndEntityForms() throws Exception {
        assertEquals("power", EebusPoint.parse("power").gatewayToken());
        assertEquals("setpoint", EebusPoint.parse("setpoint").gatewayToken());
        assertEquals("entity:ElectricalConnection:power",
                EebusPoint.parse("entity:ElectricalConnection:power").gatewayToken());
    }

    @Test
    void readAndWritePowerAndSetpoint() throws Exception {
        gateway = new FakeEebusSpineLab();
        gateway.put("power", 1234.5f);
        gateway.put("entity:ElectricalConnection:power", 1234.5f);
        gateway.put("setpoint", 50.0f);
        gateway.start();
        assertTrue(gateway.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new EebusDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "pwr", "power",
                "ent", "entity:ElectricalConnection:power",
                "sp", "setpoint"
        ));
        assertEquals(1234.5, (Double) object.variables.get("pwr").firstRow().get("value"), 0.001);
        assertEquals(1234.5, (Double) object.variables.get("ent").firstRow().get("value"), 0.001);
        assertEquals(50.0, (Double) object.variables.get("sp").firstRow().get("value"), 0.001);

        driver.writePoint("sp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 72.5)
        ));
        assertEquals(72.5f, gateway.get("setpoint"), 0.001f);
        assertEquals(72.5, (Double) object.variables.get("sp").firstRow().get("value"), 0.001);
        assertTrue(gateway.writeLatchAwait(2, TimeUnit.SECONDS));
    }

    @Test
    void codecAcceptsSpineLikeJsonRead() {
        EebusLabCodec.Request request = EebusLabCodec.parseRequest(
                "{\"op\":\"read\",\"entity\":\"ElectricalConnection\",\"path\":\"PowerConsumption\"}");
        assertTrue(request instanceof EebusLabCodec.GetRequest);
        assertEquals("power", ((EebusLabCodec.GetRequest) request).token());
    }

    private static final class FakeEebusSpineLab implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-eebus");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, Float> values = new ConcurrentHashMap<>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch writeSeen = new CountDownLatch(1);

        FakeEebusSpineLab() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String token, float value) {
            values.put(token, value);
        }

        float get(String token) {
            return values.getOrDefault(token, 0f);
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
                    EebusLabCodec.Request request = EebusLabCodec.parseRequest(line);
                    if (request instanceof EebusLabCodec.GetRequest get) {
                        float value = values.getOrDefault(get.token(),
                                values.getOrDefault(alias(get.token()), 0f));
                        out.write(("OK " + Float.toString(value) + "\n").getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } else if (request instanceof EebusLabCodec.SetRequest set) {
                        values.put(set.token(), set.value());
                        writeSeen.countDown();
                        out.write("OK\n".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static String alias(String token) {
            if ("power".equalsIgnoreCase(token)) {
                return "entity:ElectricalConnection:power";
            }
            if ("entity:ElectricalConnection:power".equalsIgnoreCase(token)) {
                return "power";
            }
            return token;
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
                    "test-eebus", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
