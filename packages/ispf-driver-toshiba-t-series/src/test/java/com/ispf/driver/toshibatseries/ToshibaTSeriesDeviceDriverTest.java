package com.ispf.driver.toshibatseries;

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
 * Loopback tests for {@link ToshibaTSeriesDeviceDriver} against a fake T-series computer-link station.
 */
class ToshibaTSeriesDeviceDriverTest {

    private ToshibaTSeriesDeviceDriver driver;
    private FakeToshibaPlc plc;

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
    void readDAndXAndYViaLoopback() throws Exception {
        plc = new FakeToshibaPlc();
        plc.setRegister("D100", 0x0100);
        plc.setRegister("X0", 1);
        plc.setRegister("Y0", 0);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "station", "00",
                "timeoutMs", "2000"
        ));
        driver = new ToshibaTSeriesDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals("toshiba-t-series", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());

        driver.readPoints(Map.of(
                "data", "D100",
                "input", "X0",
                "output", "Y0"
        ));

        assertEquals("256", object.variables.get("data").firstRow().get("value"));
        assertEquals("1", object.variables.get("input").firstRow().get("value"));
        assertEquals("0", object.variables.get("output").firstRow().get("value"));
        assertTrue(object.variables.get("data").firstRow().get("command").toString().startsWith("@00RDD00100"));
    }

    @Test
    void writeThenReadViaLoopback() throws Exception {
        plc = new FakeToshibaPlc();
        plc.setRegister("D100", 1);
        plc.setRegister("Y0", 0);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "timeoutMs", "2000"
        ));
        driver = new ToshibaTSeriesDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("sp", "D100", "out", "Y0"));
        driver.writePoint("sp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "55")
        ));
        driver.writePoint("out", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "1")
        ));

        driver.readPoints(Map.of("sp", "D100", "out", "Y0"));
        assertEquals("55", object.variables.get("sp").firstRow().get("value"));
        assertEquals("1", object.variables.get("out").firstRow().get("value"));
        assertEquals(55, plc.register("D100"));
        assertEquals(1, plc.register("Y0"));
    }

    @Test
    void helpersBuildAndParseFrames() {
        String read = ToshibaTSeriesDeviceDriver.buildReadCommand("00", "D100");
        assertTrue(read.startsWith("@00RDD00100"));
        assertTrue(read.endsWith("*"));

        String write = ToshibaTSeriesDeviceDriver.buildWriteCommand("00", "Y0", "1");
        assertTrue(write.startsWith("@00WDY000000001"));

        assertThrows(IllegalArgumentException.class, () ->
                ToshibaTSeriesDeviceDriver.buildWriteCommand("00", "X0", "1"));

        String body = "00RD0100";
        String frame = "@" + body + ToshibaTSeriesDeviceDriver.fcs(body) + "*";
        assertEquals("256", ToshibaTSeriesDeviceDriver.parseReadValue(frame));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ToshibaTSeriesDeviceDriver();
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
        driver = new ToshibaTSeriesDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("Toshiba T-series connect failed"));
    }

    private static final class FakeToshibaPlc implements AutoCloseable {

        private static final Pattern CMD = Pattern.compile(
                "^@(?<st>\\d{2})(?<op>RD|WD)(?<dev>[DXY])(?<addr>\\d{5})(?<data>[0-9A-Fa-f]*)(?<fcs>[0-9A-Fa-f]{2})\\*?$",
                Pattern.CASE_INSENSITIVE);

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-toshiba-t-series");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> registers = new ConcurrentHashMap<>();

        FakeToshibaPlc() throws IOException {
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
                return errorFrame("00");
            }
            String st = matcher.group("st");
            String op = matcher.group("op").toUpperCase(Locale.ROOT);
            String dev = matcher.group("dev").toUpperCase(Locale.ROOT);
            int addr = Integer.parseInt(matcher.group("addr"));
            String key = dev + addr;
            String bodyWithoutFcs = request.trim();
            if (bodyWithoutFcs.endsWith("*")) {
                bodyWithoutFcs = bodyWithoutFcs.substring(0, bodyWithoutFcs.length() - 1);
            }
            String withoutAt = bodyWithoutFcs.substring(1);
            String maybeBody = withoutAt.substring(0, withoutAt.length() - 2);
            String maybeFcs = withoutAt.substring(withoutAt.length() - 2);
            if (!ToshibaTSeriesDeviceDriver.fcs(maybeBody).equalsIgnoreCase(maybeFcs)) {
                return errorFrame(st);
            }
            if ("RD".equals(op)) {
                int word = registers.getOrDefault(key, 0);
                String body = st + "RD" + String.format(Locale.ROOT, "%04X", word & 0xFFFF);
                return "@" + body + ToshibaTSeriesDeviceDriver.fcs(body) + "*";
            }
            String data = matcher.group("data");
            if (data.length() >= 4) {
                int word = Integer.parseInt(data.substring(0, 4), 16);
                registers.put(key, word & 0xFFFF);
            }
            String body = st + "WD";
            return "@" + body + ToshibaTSeriesDeviceDriver.fcs(body) + "*";
        }

        private static String errorFrame(String st) {
            String body = st + "E00";
            return "@" + body + ToshibaTSeriesDeviceDriver.fcs(body) + "*";
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
                    "test-toshiba-t-series",
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
