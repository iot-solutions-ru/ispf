package com.ispf.driver.keyencehostlink;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link KeyenceHostlinkDeviceDriver} against an in-process fake Host Link PLC.
 */
class KeyenceHostlinkDeviceDriverTest {

    private KeyenceHostlinkDeviceDriver driver;
    private FakeKeyencePlc plc;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (plc != null) {
            plc.close();
            plc = null;
        }
    }

    @Test
    void readDmViaExpandedMapping() throws Exception {
        plc = new FakeKeyencePlc();
        plc.setRegister("DM100", "1234");
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "timeoutMs", "2000"
        ));
        driver = new KeyenceHostlinkDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("level", "DM100"));
        assertEquals("1234", object.variables.get("level").firstRow().get("value"));
        assertEquals("DM100", object.variables.get("level").firstRow().get("register"));
        assertEquals("RDS DM100 1", object.variables.get("level").firstRow().get("command"));
    }

    @Test
    void writeThenReadViaLoopback() throws Exception {
        plc = new FakeKeyencePlc();
        plc.setRegister("DM200", "1");
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "timeoutMs", "2000"
        ));
        driver = new KeyenceHostlinkDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("sp", "DM200"));
        driver.writePoint("sp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "55")
        ));

        driver.readPoints(Map.of("sp", "DM200"));
        assertEquals("55", object.variables.get("sp").firstRow().get("value"));
        assertEquals("55", plc.register("DM200"));
    }

    @Test
    void explicitRdsMappingAndHelpers() throws Exception {
        plc = new FakeKeyencePlc();
        plc.setRegister("R0", "1");
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port())
        ));
        driver = new KeyenceHostlinkDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("relay", "RDS R0 1"));
        assertEquals("1", object.variables.get("relay").firstRow().get("value"));

        assertEquals("RDS DM100 1", KeyenceHostlinkDeviceDriver.buildReadCommand("DM100"));
        assertEquals("WR DM100 9", KeyenceHostlinkDeviceDriver.buildWriteCommand("DM100", "9"));
        assertEquals("1234", KeyenceHostlinkDeviceDriver.parseReadValue("DM100 1234"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new KeyenceHostlinkDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("dm", "DM100")));
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
        driver = new KeyenceHostlinkDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("Keyence Host Link connect failed"));
    }

    private static final class FakeKeyencePlc implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-keyence-hostlink");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> registers = new ConcurrentHashMap<>();
        private final AtomicReference<String> lastCommand = new AtomicReference<>("");

        FakeKeyencePlc() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setRegister(String name, String value) {
            registers.put(name.toUpperCase(Locale.ROOT), value);
        }

        String register(String name) {
            return registers.get(name.toUpperCase(Locale.ROOT));
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
                    String command = KeyenceHostlinkDeviceDriver.readFrame(in);
                    lastCommand.set(command);
                    String upper = command.toUpperCase(Locale.ROOT);
                    if (upper.startsWith("RDS ") || upper.startsWith("RD ")) {
                        String[] parts = command.split("\\s+");
                        if (parts.length >= 2) {
                            String reg = parts[1].toUpperCase(Locale.ROOT);
                            String value = registers.getOrDefault(reg, "0");
                            write(out, reg + " " + value);
                        } else {
                            write(out, "E1");
                        }
                    } else if (upper.startsWith("WRS ") || upper.startsWith("WR ")) {
                        String[] parts = command.split("\\s+");
                        if (parts.length >= 3) {
                            String reg = parts[1].toUpperCase(Locale.ROOT);
                            String value = parts[parts.length - 1];
                            registers.put(reg, value);
                            write(out, "OK");
                        } else {
                            write(out, "E1");
                        }
                    } else {
                        write(out, "E0");
                    }
                }
            } catch (IOException ignored) {
                // client closed / reset
            }
        }

        private static void write(OutputStream out, String line) throws IOException {
            out.write((line + "\r").getBytes(StandardCharsets.US_ASCII));
            out.flush();
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
                    "test-keyence-hostlink",
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
