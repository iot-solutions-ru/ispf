package com.ispf.driver.codesys;

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
 * Loopback tests for {@link CodesysDeviceDriver} against an in-process CODESYS-lab text gateway.
 * Certifies the lab GET/SET dialect only — not official CODESYS Network Protocol / PLCHandler.
 */
class CodesysDeviceDriverTest {

    private CodesysDeviceDriver driver;
    private FakeCodesysLabGateway gateway;

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
    void metadataIsProductionReadWriteLabDialect() {
        driver = new CodesysDeviceDriver();
        assertEquals("codesys", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("lab"));
        assertTrue(description.contains("not official") || description.contains("not"));
    }

    @Test
    void readSymbolApplicationGvlMotorSpeed() throws Exception {
        gateway = new FakeCodesysLabGateway();
        gateway.put("Application.GVL.MotorSpeed", "1500");
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new CodesysDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("speed", "Application.GVL.MotorSpeed"));
        assertEquals("1500", object.variables.get("speed").firstRow().get("value"));
        assertEquals("Application.GVL.MotorSpeed", object.variables.get("speed").firstRow().get("symbol"));
    }

    @Test
    void writeThenReadLoopback() throws Exception {
        gateway = new FakeCodesysLabGateway();
        gateway.put("Application.GVL.MotorSpeed", "1");
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
                "timeoutMs", "2000"
        ));
        driver = new CodesysDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("speed", "Application.GVL.MotorSpeed"));
        driver.writePoint("speed", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "1800")
        ));
        assertEquals("1800", gateway.get("Application.GVL.MotorSpeed"));

        driver.readPoints(Map.of("speed", "Application.GVL.MotorSpeed"));
        assertEquals("1800", object.variables.get("speed").firstRow().get("value"));
    }

    @Test
    void normalizeSymbolAndParseOk() {
        assertEquals("Application.GVL.MotorSpeed",
                CodesysDeviceDriver.normalizeSymbol("Application.GVL.MotorSpeed"));
        assertEquals("Application.GVL.X",
                CodesysDeviceDriver.normalizeSymbol("symbol Application.GVL.X"));
        assertEquals("42", CodesysDeviceDriver.parseOkValue(
                "OK Application.GVL.MotorSpeed=42", "Application.GVL.MotorSpeed"));
        assertThrows(IllegalArgumentException.class,
                () -> CodesysDeviceDriver.normalizeSymbol("bad symbol!"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new CodesysDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("speed", "Application.GVL.MotorSpeed")));
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
        driver = new CodesysDeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("CODESYS-lab gateway connect failed"));
    }

    private static final class FakeCodesysLabGateway implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-codesys-lab");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> symbols = new ConcurrentHashMap<>();

        FakeCodesysLabGateway() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String symbol, String value) {
            symbols.put(symbol, value);
        }

        String get(String symbol) {
            return symbols.get(symbol);
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
                    String line = CodesysDeviceDriver.readLine(in);
                    String trimmed = line.trim();
                    if (trimmed.regionMatches(true, 0, "GET ", 0, 4)) {
                        String symbol = CodesysDeviceDriver.normalizeSymbol(trimmed.substring(4));
                        String value = symbols.get(symbol);
                        if (value == null) {
                            CodesysDeviceDriver.writeLine(out, "ERR unknown symbol");
                        } else {
                            CodesysDeviceDriver.writeLine(out, "OK " + symbol + "=" + value);
                        }
                    } else if (trimmed.regionMatches(true, 0, "SET ", 0, 4)) {
                        String rest = trimmed.substring(4).trim();
                        int space = rest.indexOf(' ');
                        if (space <= 0) {
                            CodesysDeviceDriver.writeLine(out, "ERR");
                            continue;
                        }
                        String symbol = CodesysDeviceDriver.normalizeSymbol(rest.substring(0, space));
                        String value = rest.substring(space + 1);
                        symbols.put(symbol, value);
                        CodesysDeviceDriver.writeLine(out, "OK " + symbol + "=" + value);
                    } else {
                        CodesysDeviceDriver.writeLine(out, "ERR");
                    }
                }
            } catch (IOException ignored) {
                // client closed
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
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-codesys",
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
