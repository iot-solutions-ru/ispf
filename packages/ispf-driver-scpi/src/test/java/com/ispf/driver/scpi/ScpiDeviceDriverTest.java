package com.ispf.driver.scpi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link ScpiDeviceDriver} against an in-process fake SCPI instrument.
 */
class ScpiDeviceDriverTest {

    /** Handwritten IEEE 488.2 *IDN? plus LF terminator (2A 49 44 4E 3F 0A). */
    private static final byte[] IDN_QUERY = new byte[] {
            0x2A, 0x49, 0x44, 0x4E, 0x3F, 0x0A
    };

    /** Exact peer identity body; FakeScpiInstrument appends LF on the wire. */
    private static final String IDN_REPLY = "ISPF,MODEL,0,1.0";

    private ScpiDeviceDriver driver;
    private FakeScpiInstrument instrument;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (instrument != null) {
            instrument.close();
            instrument = null;
        }
    }

    @Test
    void idnQueryOctetsAreHandwritten() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ScpiDeviceDriver.writeLine(out, "*IDN?");
        assertArrayEquals(IDN_QUERY, out.toByteArray());
    }

    @Test
    void metadataDescriptionOmitsLab() {
        String description = new ScpiDeviceDriver().metadata().description();
        assertFalse(description.toLowerCase(Locale.ROOT).contains("lab"));
    }

    @Test
    void idnViaLoopbackWritesHandwrittenQuery() throws Exception {
        instrument = new FakeScpiInstrument();
        instrument.setIdnReply(IDN_REPLY);
        instrument.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(instrument.port()),
                "timeoutMs", "2000"
        ));
        driver = new ScpiDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("idn", "*IDN?"));
        assertArrayEquals(IDN_QUERY, instrument.lastCommandBytes());
        assertEquals(IDN_REPLY, object.variables.get("idn").firstRow().get("value"));
        assertEquals("*IDN?", object.variables.get("idn").firstRow().get("command"));
    }

    @Test
    void idnAndMeasureViaLoopback() throws Exception {
        instrument = new FakeScpiInstrument();
        instrument.setVoltage(12.5);
        instrument.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(instrument.port()),
                "timeoutMs", "2000"
        ));
        driver = new ScpiDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "idn", "*IDN?",
                "volt", "MEAS:VOLT:DC?"
        ));
        assertEquals("ISPF,FakeInstrument,1.0,SCPI", object.variables.get("idn").firstRow().get("value"));
        assertEquals("12.5", object.variables.get("volt").firstRow().get("value"));
        assertEquals("MEAS:VOLT:DC?", object.variables.get("volt").firstRow().get("command"));
    }

    @Test
    void writeSetsVoltageAndReadback() throws Exception {
        instrument = new FakeScpiInstrument();
        instrument.setVoltage(1.0);
        instrument.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(instrument.port()),
                "timeoutMs", "2000"
        ));
        driver = new ScpiDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("source", "VOLT"));
        driver.writePoint("source", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")
        ));

        driver.readPoints(Map.of("volt", "MEAS:VOLT:DC?"));
        assertEquals("24.1", object.variables.get("volt").firstRow().get("value"));
        assertEquals("24.1", instrument.voltage());
    }

    @Test
    void writeSupportsValuePlaceholder() throws Exception {
        instrument = new FakeScpiInstrument();
        instrument.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(instrument.port())
        ));
        driver = new ScpiDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("source", "SOUR:VOLT {value}"));
        driver.writePoint("source", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "3.3")
        ));

        driver.readPoints(Map.of("volt", "MEAS:VOLT:DC?"));
        assertEquals("3.3", object.variables.get("volt").firstRow().get("value"));
        assertEquals("3.3", instrument.voltage());
    }

    @Test
    void buildWriteCommandHelpers() {
        assertEquals("VOLT 5", ScpiDeviceDriver.buildWriteCommand("VOLT", "5"));
        assertEquals("VOLT 5", ScpiDeviceDriver.buildWriteCommand("VOLT?", "5"));
        assertEquals("SOUR:VOLT 5", ScpiDeviceDriver.buildWriteCommand("SOUR:VOLT {value}", "5"));
        assertEquals("CLS", ScpiDeviceDriver.buildWriteCommand("", "CLS"));
        assertTrue(ScpiDeviceDriver.isQuery("*IDN?"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new ScpiDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("idn", "*IDN?")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void readFailsAgainstUnreachableHost() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200"
        ));
        driver = new ScpiDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("SCPI connect failed"));
    }

    private static final class FakeScpiInstrument implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-scpi-instrument");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicReference<String> voltage = new AtomicReference<>("0.0");
        private final AtomicReference<String> lastCommand = new AtomicReference<>("");
        private final AtomicReference<byte[]> lastCommandBytes = new AtomicReference<>(new byte[0]);
        private final AtomicReference<String> idnReply =
                new AtomicReference<>("ISPF,FakeInstrument,1.0,SCPI");

        FakeScpiInstrument() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setVoltage(double volts) {
            voltage.set(Double.toString(volts));
        }

        void setIdnReply(String reply) {
            idnReply.set(reply);
        }

        String voltage() {
            return voltage.get();
        }

        String lastCommand() {
            return lastCommand.get();
        }

        byte[] lastCommandBytes() {
            return lastCommandBytes.get();
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
                    String command = ScpiDeviceDriver.readLine(in);
                    lastCommand.set(command);
                    lastCommandBytes.set((command + "\n").getBytes(StandardCharsets.US_ASCII));
                    String upper = command.toUpperCase(Locale.ROOT);
                    if (upper.equals("*IDN?")) {
                        write(out, idnReply.get());
                    } else if (upper.equals("MEAS:VOLT:DC?") || upper.equals("MEAS:VOLT?")) {
                        write(out, voltage.get());
                    } else if (upper.startsWith("VOLT ") || upper.startsWith("SOUR:VOLT ")) {
                        String[] parts = command.split("\\s+", 2);
                        if (parts.length == 2) {
                            voltage.set(parts[1].trim());
                        }
                    } else if (upper.equals("SYST:ERR?")) {
                        write(out, "0,\"No error\"");
                    } else if (upper.endsWith("?")) {
                        write(out, "");
                    }
                }
            } catch (IOException ignored) {
                // client closed / reset
            }
        }

        private static void write(OutputStream out, String line) throws IOException {
            out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
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
                    "test-scpi",
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
