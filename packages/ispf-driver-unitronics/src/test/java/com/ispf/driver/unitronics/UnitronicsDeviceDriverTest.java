package com.ispf.driver.unitronics;

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
 * Loopback tests for {@link UnitronicsDeviceDriver} against an in-process PCOM ASCII peer.
 */
class UnitronicsDeviceDriverTest {

    private UnitronicsDeviceDriver driver;
    private FakePcomPlc plc;

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
    void mi0ReadIsLiteralPcomBytes() throws Exception {
        // / 00 RW 0000 01 FCS=2A CR — FCS = sum(mod 256) of ASCII after '/'
        byte[] expected = new byte[] {
                0x2F, 0x30, 0x30, 0x52, 0x57, 0x30, 0x30, 0x30, 0x30, 0x30, 0x31, 0x32, 0x41, 0x0D
        };
        String command = UnitronicsDeviceDriver.buildReadCommand("00", "MI0");
        assertEquals("/00RW0000012A", command);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        UnitronicsDeviceDriver.writeFrame(captured, command);
        assertArrayEquals(expected, captured.toByteArray());
    }

    @Test
    void readMiAndMbViaLoopback() throws Exception {
        plc = new FakePcomPlc();
        plc.setRegister("MI100", 4660);
        plc.setRegister("MB0", 1);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "unitId", "00",
                "timeoutMs", "2000"
        ));
        driver = new UnitronicsDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals("unitronics", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());

        driver.readPoints(Map.of(
                "int", "MI100",
                "bit", "MB0"
        ));

        assertEquals("4660", object.variables.get("int").firstRow().get("value"));
        assertEquals("1", object.variables.get("bit").firstRow().get("value"));
        assertEquals("/00RW00640134", object.variables.get("int").firstRow().get("command"));
    }

    @Test
    void writeThenReadViaLoopback() throws Exception {
        plc = new FakePcomPlc();
        plc.setRegister("MI200", 1);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "timeoutMs", "2000"
        ));
        driver = new UnitronicsDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("sp", "MI200"));
        driver.writePoint("sp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "99")
        ));

        driver.readPoints(Map.of("sp", "MI200"));
        assertEquals("99", object.variables.get("sp").firstRow().get("value"));
        assertEquals(99, plc.register("MI200"));
    }

    @Test
    void helpersBuildAndParseFrames() {
        assertEquals("/00RW0000012A", UnitronicsDeviceDriver.buildReadCommand("00", "MI0"));
        assertEquals("/00RB00000115", UnitronicsDeviceDriver.buildReadCommand("00", "MB0"));

        String write = UnitronicsDeviceDriver.buildWriteCommand("00", "MB0", "1");
        assertTrue(write.startsWith("/00SB0000011"));

        assertEquals("4660", UnitronicsDeviceDriver.parseReadValue("/A00RW1234D3"));
        assertEquals("1", UnitronicsDeviceDriver.parseReadValue(
                "/A00RB1" + UnitronicsDeviceDriver.fcs("00RB1")));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new UnitronicsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("mi", "MI100")));
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
        driver = new UnitronicsDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("Unitronics connect failed"));
    }

    private static final class FakePcomPlc implements AutoCloseable {

        private static final Pattern CMD = Pattern.compile(
                "^/(?<unit>\\d{2})(?<cc>[A-Za-z]{2})(?<addr>[0-9A-Fa-f]{4})"
                        + "(?<count>[0-9A-Fa-f]{2})(?<data>[0-9A-Fa-f]*)(?<fcs>[0-9A-Fa-f]{2})$");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-unitronics-pcom");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> registers = new ConcurrentHashMap<>();

        FakePcomPlc() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setRegister(String name, int value) {
            registers.put(name.toUpperCase(Locale.ROOT), value);
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
                    String request = readLine(in);
                    if (request == null) {
                        return;
                    }
                    String response = buildResponse(request);
                    out.write((response + "\r").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        }

        private String buildResponse(String request) {
            Matcher matcher = CMD.matcher(request.trim());
            if (!matcher.matches()) {
                String body = "N00";
                return "/" + body + UnitronicsDeviceDriver.fcs(body);
            }
            String unit = matcher.group("unit");
            String cc = matcher.group("cc").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"), 16);
            String data = matcher.group("data");
            String bodyWithoutFcs = request.trim().substring(1, request.trim().length() - 2);
            String fcs = matcher.group("fcs");
            if (!UnitronicsDeviceDriver.fcs(bodyWithoutFcs).equalsIgnoreCase(fcs)) {
                String body = "N" + unit;
                return "/" + body + UnitronicsDeviceDriver.fcs(body);
            }
            if ("RW".equals(cc) || "RB".equals(cc)) {
                String device = "RW".equals(cc) ? "MI" : "MB";
                int word = registers.getOrDefault(device + addr, 0);
                String payload;
                String respCc;
                if ("MB".equals(device)) {
                    payload = word != 0 ? "1" : "0";
                    respCc = "RB";
                } else {
                    payload = String.format(Locale.ROOT, "%04X", word & 0xFFFF);
                    respCc = "RW";
                }
                String body = unit + respCc + payload;
                return "/A" + body + UnitronicsDeviceDriver.fcs(body);
            }
            if ("SW".equals(cc) || "SB".equals(cc)) {
                String device = "SW".equals(cc) ? "MI" : "MB";
                int word;
                if ("MB".equals(device)) {
                    word = data.isEmpty() || data.charAt(0) == '0' ? 0 : 1;
                } else {
                    word = Integer.parseInt(data.substring(0, Math.min(4, data.length())), 16);
                }
                registers.put(device + addr, word);
                String body = unit + cc;
                return "/A" + body + UnitronicsDeviceDriver.fcs(body);
            }
            String body = "N" + unit;
            return "/" + body + UnitronicsDeviceDriver.fcs(body);
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int ch = in.read();
                if (ch < 0) {
                    return sb.length() == 0 ? null : sb.toString();
                }
                if (ch == '\r' || ch == '\n') {
                    if (sb.length() == 0) {
                        continue;
                    }
                    return sb.toString();
                }
                sb.append((char) ch);
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
                    "test-unitronics",
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
