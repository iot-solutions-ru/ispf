package com.ispf.driver.canbusgateway;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanbusGatewayDeviceDriverTest {

    private CanbusGatewayDeviceDriver driver;
    private FakeCanGateway gateway;

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
    void metadataIsProductionReadWrite() {
        driver = new CanbusGatewayDeviceDriver();
        assertEquals("canbus-gateway", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void getAndTxLoopback() throws Exception {
        gateway = new FakeCanGateway();
        gateway.put("18FF50E5", "AABBCCDD");
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new CanbusGatewayDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("engine", "0x18FF50E5"));
        assertEquals("AABBCCDD", object.variables.get("engine").firstRow().get("value"));

        driver.writePoint("engine", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "01020304")
        ));
        assertEquals("01020304", gateway.get("18FF50E5"));
    }

    private static final class FakeCanGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-can-gw");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, String> frames = new ConcurrentHashMap<>();

        FakeCanGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String canId, String data) {
            frames.put(canId.toUpperCase(Locale.ROOT), data.toUpperCase(Locale.ROOT));
        }

        String get(String canId) {
            return frames.get(canId.toUpperCase(Locale.ROOT));
        }

        void start() {
            executor.submit(this::acceptLoop);
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
                    String command = CanbusGatewayDeviceDriver.readLine(in);
                    if (command == null) {
                        return;
                    }
                    String upper = command.trim().toUpperCase(Locale.ROOT);
                    if (upper.startsWith("GET ")) {
                        String id = upper.substring(4).trim();
                        String data = frames.getOrDefault(id, "");
                        CanbusGatewayDeviceDriver.writeLine(out, "RX " + id + " " + data);
                    } else if (upper.startsWith("TX ")) {
                        String[] parts = upper.substring(3).trim().split("\\s+");
                        String id = parts[0];
                        String data = parts.length > 1 ? parts[1] : "";
                        frames.put(id, data);
                        CanbusGatewayDeviceDriver.writeLine(out, "OK " + id + " " + data);
                    } else {
                        CanbusGatewayDeviceDriver.writeLine(out, "ERR");
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
            return new PlatformObject("test-can", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
