package com.ispf.driver.panasonicmewto;

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

import java.io.ByteArrayOutputStream;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link PanasonicMewtoDeviceDriver} against an in-process MEWTOCOL peer.
 */
class PanasonicMewtoDeviceDriverTest {

    private PanasonicMewtoDeviceDriver driver;
    private FakeMewtocolPlc plc;

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
    void dt0ReadIsLiteralMewtocolBytes() throws Exception {
        // % 01 # RDD 00000 00000 BCC=70 CR — BCC = XOR of bytes after '%'
        byte[] expected = new byte[] {
                0x25, 0x30, 0x31, 0x23, 0x52, 0x44, 0x44,
                0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
                0x37, 0x30, 0x0D
        };
        String command = PanasonicMewtoDeviceDriver.buildReadCommand("01", "DT0");
        assertEquals("%01#RDD000000000070", command);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PanasonicMewtoDeviceDriver.writeFrame(captured, command);
        assertArrayEquals(expected, captured.toByteArray());
    }

    @Test
    void readDRegisterViaExpandedMapping() throws Exception {
        plc = new FakeMewtocolPlc();
        plc.setRegister("D100", 42);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "station", "01",
                "timeoutMs", "2000"
        ));
        driver = new PanasonicMewtoDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals("panasonic-mewto", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());

        driver.readPoints(Map.of("level", "D100"));
        assertEquals("42", object.variables.get("level").firstRow().get("value"));
        assertEquals("D100", object.variables.get("level").firstRow().get("register"));
        assertEquals("%01#RDD001000010070", object.variables.get("level").firstRow().get("command"));
    }

    @Test
    void writeThenReadViaLoopback() throws Exception {
        plc = new FakeMewtocolPlc();
        plc.setRegister("D200", 1);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "timeoutMs", "2000"
        ));
        driver = new PanasonicMewtoDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("sp", "D200"));
        driver.writePoint("sp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "77")
        ));

        driver.readPoints(Map.of("sp", "D200"));
        assertEquals("77", object.variables.get("sp").firstRow().get("value"));
        assertEquals(77, plc.register("D200"));
    }

    @Test
    void rContactAndBccHelpers() throws Exception {
        plc = new FakeMewtocolPlc();
        plc.setRegister("R0", 1);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port())
        ));
        driver = new PanasonicMewtoDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("relay", "R0"));
        assertEquals("1", object.variables.get("relay").firstRow().get("value"));

        String body = "01#RDD0000000000";
        String frame = "%" + body + PanasonicMewtoDeviceDriver.bcc(body);
        assertEquals(frame, PanasonicMewtoDeviceDriver.ensureBccFrame("%" + body + "**"));
        assertEquals("70", PanasonicMewtoDeviceDriver.bcc(body));
        assertEquals("42", PanasonicMewtoDeviceDriver.parseReadValue(
                "%01$RD2A00" + PanasonicMewtoDeviceDriver.bcc("01$RD2A00")));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new PanasonicMewtoDeviceDriver();
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
        driver = new PanasonicMewtoDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("Panasonic MEWTOCOL connect failed"));
    }

    private static final class FakeMewtocolPlc implements AutoCloseable {

        private static final Pattern CMD = Pattern.compile(
                "^%(?<st>\\d{2})#(?<cmd>[A-Z]{2,4})(?<rest>.*?)(?<bcc>[0-9A-Fa-f]{2})$");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-panasonic-mewto");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> registers = new ConcurrentHashMap<>();

        FakeMewtocolPlc() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setRegister(String name, int value) {
            registers.put(name.toUpperCase(Locale.ROOT), value & 0xFFFF);
        }

        int register(String name) {
            return registers.getOrDefault(name.toUpperCase(Locale.ROOT), 0);
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
                    String command = PanasonicMewtoDeviceDriver.readFrame(in);
                    Matcher matcher = CMD.matcher(command);
                    if (!matcher.matches()) {
                        write(out, errorFrame("01"));
                        continue;
                    }
                    String st = matcher.group("st");
                    String cmd = matcher.group("cmd").toUpperCase(Locale.ROOT);
                    String rest = matcher.group("rest");
                    String bcc = matcher.group("bcc");
                    String body = st + "#" + cmd + rest;
                    if (!PanasonicMewtoDeviceDriver.bcc(body).equalsIgnoreCase(bcc)) {
                        write(out, errorFrame(st));
                        continue;
                    }
                    if (cmd.equals("RDD") || cmd.equals("RCC")) {
                        String device = cmd.startsWith("RD") && cmd.endsWith("D") ? "D" : "R";
                        int addr = Integer.parseInt(rest.substring(0, 5));
                        String reg = device + addr;
                        int value = registers.getOrDefault(reg, 0);
                        String data;
                        if ("D".equals(device)) {
                            data = String.format(Locale.ROOT, "%02X%02X", value & 0xFF, (value >> 8) & 0xFF);
                        } else {
                            data = String.valueOf(value != 0 ? 1 : 0);
                        }
                        String respBody = st + "$" + (device.equals("D") ? "RD" : "RC") + data;
                        write(out, "%" + respBody + PanasonicMewtoDeviceDriver.bcc(respBody));
                    } else if (cmd.equals("WDD") || cmd.equals("WCC")) {
                        String device = cmd.startsWith("WD") && cmd.endsWith("D") ? "D" : "R";
                        int addr = Integer.parseInt(rest.substring(0, 5));
                        String data = rest.substring(10);
                        int word;
                        if ("D".equals(device) && data.length() >= 4) {
                            int low = Integer.parseInt(data.substring(0, 2), 16);
                            int high = Integer.parseInt(data.substring(2, 4), 16);
                            word = (high << 8) | low;
                        } else {
                            word = Integer.parseInt(data);
                        }
                        registers.put(device + addr, word & 0xFFFF);
                        String respBody = st + "$WC";
                        write(out, "%" + respBody + PanasonicMewtoDeviceDriver.bcc(respBody));
                    } else {
                        write(out, errorFrame(st));
                    }
                }
            } catch (IOException ignored) {
                // client closed / reset
            }
        }

        private static String errorFrame(String st) {
            String body = st + "!ER";
            return "%" + body + PanasonicMewtoDeviceDriver.bcc(body);
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
                    "test-panasonic-mewto",
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
