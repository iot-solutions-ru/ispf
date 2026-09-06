package com.ispf.driver.weighbridge;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMaturity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeighbridgeDeviceDriverTest {

    private WeighbridgeDeviceDriver driver;
    private FakeScale scale;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (scale != null) {
            scale.close();
            scale = null;
        }
    }

    @Test
    void metadataIsProductionReadWrite() {
        driver = new WeighbridgeDeviceDriver();
        assertEquals("weighbridge", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void pollWeightAndZeroLoopback() throws Exception {
        scale = new FakeScale();
        scale.setWeight(123.4);
        scale.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(scale.port()),
                "timeoutMs", "2000"
        ));
        driver = new WeighbridgeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("gross", "weight"));
        assertEquals("123.4", object.variables.get("gross").firstRow().get("value"));
        assertEquals("kg", object.variables.get("gross").firstRow().get("unit"));
        assertEquals("ST", object.variables.get("gross").firstRow().get("status"));

        driver.writePoint("gross", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "ZERO")
        ));
        assertEquals("0.0", scale.weight());
        assertEquals("ZERO", scale.lastCommand());

        driver.readPoints(Map.of("gross", "weight"));
        assertEquals("0.0", object.variables.get("gross").firstRow().get("value"));
    }

    @Test
    void parseSignedWeightLine() {
        WeighbridgeDeviceDriver.ParsedWeight parsed =
                WeighbridgeDeviceDriver.parseWeight("ST,GS,+000256.7kg");
        assertEquals("000256.7", parsed.value());
        assertEquals("kg", parsed.unit());
        assertEquals("ST", parsed.status());
    }

    private static final class FakeScale implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-weighbridge");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<String> weight = new AtomicReference<>("0.0");
        private final AtomicReference<String> lastCommand = new AtomicReference<>("");

        FakeScale() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setWeight(double kg) {
            weight.set(Double.toString(kg));
        }

        String weight() {
            return weight.get();
        }

        String lastCommand() {
            return lastCommand.get();
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException ignored) {
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    String command = WeighbridgeDeviceDriver.readLine(in);
                    if (command == null) {
                        break;
                    }
                    String upper = command.trim().toUpperCase(Locale.ROOT);
                    lastCommand.set(upper);
                    if ("W".equals(upper)) {
                        WeighbridgeDeviceDriver.writeLine(out, "ST,GS,+" + weight.get() + "kg");
                    } else if ("ZERO".equals(upper)) {
                        weight.set("0.0");
                        WeighbridgeDeviceDriver.writeLine(out, "OK");
                    } else if ("TARE".equals(upper)) {
                        WeighbridgeDeviceDriver.writeLine(out, "OK");
                    } else {
                        WeighbridgeDeviceDriver.writeLine(out, "ERR");
                    }
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
            return new PlatformObject("test-wb", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
