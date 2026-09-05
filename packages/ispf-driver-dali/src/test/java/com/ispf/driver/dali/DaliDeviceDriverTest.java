package com.ispf.driver.dali;

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

class DaliDeviceDriverTest {

    private DaliDeviceDriver driver;
    private FakeDaliGateway gateway;

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
        driver = new DaliDeviceDriver();
        assertEquals("dali", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void queryAndSetLoopback() throws Exception {
        gateway = new FakeDaliGateway();
        gateway.setLevel("A5", 120);
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new DaliDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("lamp", "A5"));
        assertEquals("120", object.variables.get("lamp").firstRow().get("value"));

        driver.writePoint("lamp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "200")
        ));
        assertEquals(200, gateway.level("A5"));
        driver.readPoints(Map.of("lamp", "A5"));
        assertEquals("200", object.variables.get("lamp").firstRow().get("value"));
    }

    private static final class FakeDaliGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-dali");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, Integer> levels = new ConcurrentHashMap<>();

        FakeDaliGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setLevel(String address, int level) {
            levels.put(address.toUpperCase(Locale.ROOT), level);
        }

        int level(String address) {
            return levels.getOrDefault(address.toUpperCase(Locale.ROOT), 0);
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
                    String command = DaliDeviceDriver.readLine(in);
                    if (command == null) {
                        return;
                    }
                    String upper = command.trim().toUpperCase(Locale.ROOT);
                    if (upper.startsWith("QUERY ")) {
                        String addr = upper.substring(6).trim();
                        DaliDeviceDriver.writeLine(out, "LEVEL " + levels.getOrDefault(addr, 0));
                    } else if (upper.startsWith("SET ")) {
                        String[] parts = upper.substring(4).trim().split("\\s+");
                        String addr = parts[0];
                        int level = Integer.parseInt(parts[1]);
                        levels.put(addr, level);
                        DaliDeviceDriver.writeLine(out, "OK " + level);
                    } else {
                        DaliDeviceDriver.writeLine(out, "ERR");
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
            return new PlatformObject("test-dali", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
