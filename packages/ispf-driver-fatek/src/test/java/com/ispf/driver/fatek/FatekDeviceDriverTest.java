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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process ServerSocket peer tests for Fatek FACON ASCII (command 46/47).
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
    void r0ReadIsLiteralFaconBytes() {
        // Fatek Appendix cmd 46: station 01, N=01, start R00000.
        // LRC = low byte of sum(STX + "014601R00000") = 0x70 (published span c～f).
        // Octets written here — not derived from FatekDeviceDriver.lrc / frame.
        byte[] expected = new byte[] {
                0x02,
                0x30, 0x31,
                0x34, 0x36,
                0x30, 0x31,
                0x52, 0x30, 0x30, 0x30, 0x30, 0x30,
                0x37, 0x30,
                0x03
        };
        String command = FatekDeviceDriver.buildReadCommand("01", "R0");
        assertArrayEquals(expected, command.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void publishedR12ExampleLrcMatchesManual() {
        // Manual example: STX 014603R00012 75 ETX
        assertEquals("75", FatekDeviceDriver.lrc("014603R00012"));
        String frame = FatekDeviceDriver.frame("01", "4603R00012");
        assertEquals(
                "\u0002014603R0001275\u0003",
                frame
        );
    }

    @Test
    void readR0AndD100ViaLoopback() throws Exception {
        plc = new FakeFatekPlc();
        plc.setRegister("R0", 0x04D2);
        plc.setRegister("D100", 0x1234);
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

        driver.readPoints(Map.of(
                "relay", "R0",
                "level", "D100"
        ));
        assertEquals("1234", object.variables.get("relay").firstRow().get("value"));
        assertEquals("4660", object.variables.get("level").firstRow().get("value"));
        assertEquals("R0", object.variables.get("relay").firstRow().get("register"));
        assertTrue(object.variables.get("relay").firstRow().get("command").toString().contains("4601R00000"));
    }

    @Test
    void writeThenReadViaLoopback() throws Exception {
        plc = new FakeFatekPlc();
        plc.setRegister("D200", 1);
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
        assertEquals(88, plc.register("D200"));
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

        private static final Pattern CMD46 = Pattern.compile(
                "^(?<st>[0-9A-Fa-f]{2})46(?<n>[0-9A-Fa-f]{2})(?<reg>[RDMXY][0-9]{5})$",
                Pattern.CASE_INSENSITIVE);
        private static final Pattern CMD47 = Pattern.compile(
                "^(?<st>[0-9A-Fa-f]{2})47(?<n>[0-9A-Fa-f]{2})(?<reg>[RDMXY][0-9]{5})(?<val>[0-9A-Fa-f]{4})$",
                Pattern.CASE_INSENSITIVE);

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-fatek-facon");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> registers = new ConcurrentHashMap<>();

        FakeFatekPlc() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setRegister(String name, int value) {
            registers.put(canonical(name), value & 0xFFFF);
        }

        int register(String name) {
            return registers.getOrDefault(canonical(name), 0);
        }

        void start() {
            var _ = executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    var _ = executor.submit(() -> handle(socket));
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
                        writeError(out, "01", "46");
                        continue;
                    }
                    String body = inner.substring(0, inner.length() - 2);
                    String chk = inner.substring(inner.length() - 2);
                    if (!FatekDeviceDriver.lrc(body).equalsIgnoreCase(chk)) {
                        writeError(out, body.substring(0, 2), body.substring(2, 4));
                        continue;
                    }
                    Matcher read = CMD46.matcher(body);
                    if (read.matches()) {
                        String st = read.group("st").toUpperCase(Locale.ROOT);
                        String reg = canonical(read.group("reg"));
                        int value = registers.getOrDefault(reg, 0);
                        String data = String.format(Locale.ROOT, "%04X", value & 0xFFFF);
                        write(out, FatekDeviceDriver.frame(st, "46" + "0" + data));
                        continue;
                    }
                    Matcher write = CMD47.matcher(body);
                    if (write.matches()) {
                        String st = write.group("st").toUpperCase(Locale.ROOT);
                        String reg = canonical(write.group("reg"));
                        int value = Integer.parseInt(write.group("val"), 16);
                        registers.put(reg, value & 0xFFFF);
                        write(out, FatekDeviceDriver.frame(st, "47" + "0"));
                        continue;
                    }
                    writeError(out, body.substring(0, 2), body.length() >= 4 ? body.substring(2, 4) : "46");
                }
            } catch (IOException ignored) {
                // client closed / reset
            }
        }

        private static String canonical(String name) {
            Matcher matcher = Pattern.compile("^([RDMXY])0*(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(name.trim());
            if (matcher.matches()) {
                return matcher.group(1).toUpperCase(Locale.ROOT) + Integer.parseInt(matcher.group(2));
            }
            return name.toUpperCase(Locale.ROOT);
        }

        private static void writeError(OutputStream out, String st, String cmd) throws IOException {
            write(out, FatekDeviceDriver.frame(st, cmd + "2"));
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
