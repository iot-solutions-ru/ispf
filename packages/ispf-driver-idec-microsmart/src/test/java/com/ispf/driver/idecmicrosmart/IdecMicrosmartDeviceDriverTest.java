package com.ispf.driver.idecmicrosmart;

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
 * Loopback tests for {@link IdecMicrosmartDeviceDriver} against a fake Host Link PLC.
 */
class IdecMicrosmartDeviceDriverTest {

    private IdecMicrosmartDeviceDriver driver;
    private FakeIdecPlc plc;

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
    void readDAndMViaLoopback() throws Exception {
        plc = new FakeIdecPlc();
        plc.setRegister("D100", 0x1234);
        plc.setRegister("M0", 1);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "station", "00",
                "timeoutMs", "2000"
        ));
        driver = new IdecMicrosmartDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals("idec-microsmart", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());

        driver.readPoints(Map.of(
                "data", "D100",
                "relay", "M0"
        ));

        assertEquals("4660", object.variables.get("data").firstRow().get("value"));
        assertEquals("1", object.variables.get("relay").firstRow().get("value"));
        assertTrue(object.variables.get("data").firstRow().get("command").toString().startsWith("@00RDD00100"));
    }

    @Test
    void writeThenReadViaLoopback() throws Exception {
        plc = new FakeIdecPlc();
        plc.setRegister("D200", 1);
        plc.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(plc.port()),
                "timeoutMs", "2000"
        ));
        driver = new IdecMicrosmartDeviceDriver();
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
    void helpersBuildAndParseFrames() {
        String read = IdecMicrosmartDeviceDriver.buildReadCommand("00", "D100");
        assertTrue(read.startsWith("@00RDD00100"));
        assertTrue(read.endsWith("*"));

        String write = IdecMicrosmartDeviceDriver.buildWriteCommand("00", "M0", "1");
        assertTrue(write.startsWith("@00WDM000000001"));

        String body = "00RD0042";
        String frame = "@" + body + IdecMicrosmartDeviceDriver.fcs(body) + "*";
        assertEquals("66", IdecMicrosmartDeviceDriver.parseReadValue(frame));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new IdecMicrosmartDeviceDriver();
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
        driver = new IdecMicrosmartDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("IDEC MicroSmart connect failed"));
    }

    private static final class FakeIdecPlc implements AutoCloseable {

        private static final Pattern CMD = Pattern.compile(
                "^@(?<st>\\d{2})(?<op>RD|WD)(?<dev>[DMR])(?<addr>\\d{5})(?<data>[0-9A-Fa-f]*)(?<fcs>[0-9A-Fa-f]{2})\\*?$");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-idec-hostlink");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> registers = new ConcurrentHashMap<>();

        FakeIdecPlc() throws IOException {
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
            String op = matcher.group("op");
            String dev = matcher.group("dev");
            int addr = Integer.parseInt(matcher.group("addr"));
            String key = dev + addr;
            if ("RD".equals(op)) {
                int word = registers.getOrDefault(key, 0);
                String body = st + "RD" + String.format(Locale.ROOT, "%04X", word & 0xFFFF);
                return "@" + body + IdecMicrosmartDeviceDriver.fcs(body) + "*";
            }
            String data = matcher.group("data");
            if (data.length() >= 4) {
                int word = Integer.parseInt(data.substring(0, 4), 16);
                registers.put(key, word & 0xFFFF);
            }
            String body = st + "WD";
            return "@" + body + IdecMicrosmartDeviceDriver.fcs(body) + "*";
        }

        private static String errorFrame(String st) {
            String body = st + "E00";
            return "@" + body + IdecMicrosmartDeviceDriver.fcs(body) + "*";
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
                    "test-idec",
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
