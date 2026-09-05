package com.ispf.driver.canopen;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link CanopenDeviceDriver} against an in-process CANopen TCP gateway lab.
 * Certifies the SDO GET/SET lab dialect only — not SocketCAN / CiA / Vector-Peak stacks.
 */
class CanopenDeviceDriverTest {

    private CanopenDeviceDriver driver;
    private FakeCanopenGateway gateway;

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
    void metadataIsProductionReadWriteTcpGatewayLab() {
        driver = new CanopenDeviceDriver();
        assertEquals("canopen", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase(Locale.ROOT).contains("tcp"));
        assertTrue(driver.metadata().description().toLowerCase(Locale.ROOT).contains("not"));
    }

    @Test
    void readHexAndDecimalOdMappings() throws Exception {
        gateway = new FakeCanopenGateway();
        gateway.put(0x2000, 0x01, "42");
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new CanopenDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("speed", "0x2000:01"));
        assertEquals("42", object.variables.get("speed").firstRow().get("value"));
        assertEquals("0x2000", object.variables.get("speed").firstRow().get("index"));
        assertEquals("01", object.variables.get("speed").firstRow().get("sub"));

        driver.readPoints(Map.of("speedDec", "2000:1"));
        assertEquals("42", object.variables.get("speedDec").firstRow().get("value"));
        assertEquals(0x2000, CanopenDeviceDriver.parseOdMapping("0x2000:01").index());
        assertEquals(1, CanopenDeviceDriver.parseOdMapping("0x2000:01").sub());
    }

    @Test
    void writeThenReadSdo() throws Exception {
        gateway = new FakeCanopenGateway();
        gateway.put(0x2000, 0x01, "0");
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new CanopenDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("od", "0x2000:01"));
        driver.writePoint("od", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "99")
        ));
        assertEquals("99", gateway.get(0x2000, 0x01));

        driver.readPoints(Map.of("od", "0x2000:01"));
        assertEquals("99", object.variables.get("od").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new CanopenDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("x", "0x2000:01")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void connectFailsAgainstUnreachableHost() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200"
        ));
        driver = new CanopenDeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("CANopen TCP gateway connect failed"));
    }

    private static final class FakeCanopenGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-canopen-gateway");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> values = new ConcurrentHashMap<>();

        FakeCanopenGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int index, int sub, String value) {
            values.put(key(index, sub), value);
        }

        String get(int index, int sub) {
            return values.get(key(index, sub));
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
                    String line = CanopenDeviceDriver.readLine(in);
                    String trimmed = line.trim();
                    if (trimmed.regionMatches(true, 0, "SDO GET ", 0, 8)) {
                        CanopenDeviceDriver.OdAddress address =
                                CanopenDeviceDriver.parseOdMapping(trimmed.substring(8).trim());
                        String value = values.get(key(address.index(), address.sub()));
                        if (value == null) {
                            CanopenDeviceDriver.writeLine(out, "ERR unknown OD");
                        } else {
                            CanopenDeviceDriver.writeLine(out, value);
                        }
                    } else if (trimmed.regionMatches(true, 0, "SDO SET ", 0, 8)) {
                        String rest = trimmed.substring(8).trim();
                        int space = rest.indexOf(' ');
                        if (space < 0) {
                            CanopenDeviceDriver.writeLine(out, "ERR");
                            continue;
                        }
                        CanopenDeviceDriver.OdAddress address =
                                CanopenDeviceDriver.parseOdMapping(rest.substring(0, space));
                        String value = rest.substring(space + 1).trim();
                        values.put(key(address.index(), address.sub()), value);
                        CanopenDeviceDriver.writeLine(out, "OK");
                    } else {
                        CanopenDeviceDriver.writeLine(out, "ERR");
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private static String key(int index, int sub) {
            return index + ":" + sub;
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
                    "test-canopen",
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
