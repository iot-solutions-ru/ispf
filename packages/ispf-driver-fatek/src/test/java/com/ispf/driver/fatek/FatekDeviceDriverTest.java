package com.ispf.driver.fatek;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link FatekDeviceDriver} against an in-process fake FACON station.
 */
class FatekDeviceDriverTest {

    private FatekDeviceDriver driver;
    private FakeFatekPlc plc;

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
    void readDRegisterViaExpandedMapping() throws Exception {
        plc = new FakeFatekPlc();
        plc.setRegister("D100", "1234");
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "station", "01",
                "timeoutMs", "2000"
        ));
        driver = new FatekDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("level", "D100"));
        assertEquals("1234", object.variables.get("level").firstRow().get("value"));
        assertEquals("D100", object.variables.get("level").firstRow().get("register"));
        assertTrue(object.variables.get("level").firstRow().get("command").toString().contains("RD100"));
    }

    @Test
    void writeThenReadViaLoopback() throws Exception {
        plc = new FakeFatekPlc();
        plc.setRegister("D200", "1");
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "timeoutMs", "2000"
        ));
        driver = new FatekDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("sp", "D200"));
        driver.writePoint("sp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "88")
        ));

        driver.readPoints(Map.of("sp", "D200"));
        assertEquals("88", object.variables.get("sp").firstRow().get("value"));
        assertEquals("88", plc.register("D200"));
    }

    @Test
    void shorthand01RAndHelpers() throws Exception {
        plc = new FakeFatekPlc();
        plc.setRegister("R0", "1");
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port())
        ));
        driver = new FatekDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("relay", "01R R0"));
        assertEquals("1", object.variables.get("relay").firstRow().get("value"));

        String frame = FatekDeviceDriver.buildReadCommand("01", "D100");
        assertTrue(frame.charAt(0) == FatekDeviceDriver.STX);
        assertTrue(frame.charAt(frame.length() - 1) == FatekDeviceDriver.ETX);
        assertEquals("1234", FatekDeviceDriver.parseReadValue(
                FatekDeviceDriver.frame("01", "01234")));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new FatekDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("d", "D100")));
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
        driver = new FatekDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("Fatek FACON connect failed"));
    }

    private static final class FakeFatekPlc implements AutoCloseable {

        private static final Pattern CMD = Pattern.compile(
                "^(?<st>\\d{2})(?<op>[RW])(?<reg>[RDMXY]\\d+)(?:=(?<val>.*))?$",
                Pattern.CASE_INSENSITIVE);

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-fatek-facon");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> registers = new ConcurrentHashMap<>();

        FakeFatekPlc() throws IOException {
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
                    String command = FatekDeviceDriver.readFrame(in);
                    String inner = FatekDeviceDriver.stripStxEtx(command);
                    if (inner.length() < 5) {
                        writeError(out, "01");
                        continue;
                    }
                    String body = inner.substring(0, inner.length() - 2);
                    String chk = inner.substring(inner.length() - 2);
                    if (!FatekDeviceDriver.lrc(body).equalsIgnoreCase(chk)) {
                        writeError(out, body.substring(0, 2));
                        continue;
                    }
                    Matcher matcher = CMD.matcher(body);
                    if (!matcher.matches()) {
                        writeError(out, body.substring(0, 2));
                        continue;
                    }
                    String st = matcher.group("st");
                    String op = matcher.group("op").toUpperCase(Locale.ROOT);
                    String reg = matcher.group("reg").toUpperCase(Locale.ROOT);
                    if ("R".equals(op)) {
                        String value = registers.getOrDefault(reg, "0");
                        write(out, FatekDeviceDriver.frame(st, "0" + value));
                    } else {
                        String val = matcher.group("val");
                        registers.put(reg, val == null ? "" : val);
                        write(out, FatekDeviceDriver.frame(st, "0"));
                    }
                }
            } catch (IOException ignored) {
                // client closed / reset
            }
        }

        private static void writeError(OutputStream out, String st) throws IOException {
            write(out, FatekDeviceDriver.frame(st, "1"));
        }

        private static void write(OutputStream out, String frame) throws IOException {
            out.write(frame.getBytes(StandardCharsets.US_ASCII));
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
                    "test-fatek",
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
