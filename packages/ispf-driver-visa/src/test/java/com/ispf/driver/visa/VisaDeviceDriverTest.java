package com.ispf.driver.visa;

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
 * Loopback tests for {@link VisaDeviceDriver} SOCKET-only SCPI-over-TCP facade.
 */
class VisaDeviceDriverTest {

    private VisaDeviceDriver driver;
    private FakeSocketInstrument instrument;

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
    void socketResourceQueryAndWrite() throws Exception {
        instrument = new FakeSocketInstrument();
        instrument.setVoltage(5.0);
        instrument.start();

        String resource = "TCPIP0::127.0.0.1::" + instrument.port() + "::SOCKET";
        StubDriverObject object = new StubDriverObject(Map.of(
                "resource", resource,
                "timeoutMs", "2000"
        ));
        driver = new VisaDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "idn", "*IDN?",
                "volt", "MEAS:VOLT:DC?"
        ));
        assertEquals("ISPF,FakeSocketInstrument,1.0,SOCKET",
                object.variables.get("idn").firstRow().get("value"));
        assertEquals("5.0", object.variables.get("volt").firstRow().get("value"));
        assertEquals(resource, object.variables.get("volt").firstRow().get("resource"));

        driver.writePoint("volt", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "9.9")
        ));

        driver.readPoints(Map.of("volt", "MEAS:VOLT:DC?"));
        assertEquals("9.9", object.variables.get("volt").firstRow().get("value"));
        assertEquals("9.9", instrument.voltage());
    }

    @Test
    void hostPortConfigBuildsSocketResource() throws Exception {
        instrument = new FakeSocketInstrument();
        instrument.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(instrument.port())
        ));
        driver = new VisaDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("idn", "*IDN?"));
        assertEquals("ISPF,FakeSocketInstrument,1.0,SOCKET",
                object.variables.get("idn").firstRow().get("value"));
        assertTrue(object.variables.get("idn").firstRow().get("resource").toString()
                .endsWith("::SOCKET"));
    }

    @Test
    void rejectsInstrAndGpibResources() {
        assertThrows(DriverException.class, () ->
                VisaDeviceDriver.parseSocketResource("TCPIP0::127.0.0.1::inst0::INSTR"));
        assertThrows(DriverException.class, () ->
                VisaDeviceDriver.parseSocketResource("GPIB0::1::INSTR"));
        assertThrows(DriverException.class, () ->
                VisaDeviceDriver.parseSocketResource("USB0::0x1234::0x5678::INSTR"));

        DriverException error = assertThrows(DriverException.class, () ->
                VisaDeviceDriver.parseSocketResource("TCPIP0::127.0.0.1::5025::INSTR"));
        assertTrue(error.getMessage().contains("SOCKET"));
        assertTrue(error.getMessage().contains("not NI-VISA"));
    }

    @Test
    void parseSocketResourceAcceptsBoardOptional() throws Exception {
        VisaDeviceDriver.SocketEndpoint endpoint =
                VisaDeviceDriver.parseSocketResource("TCPIP::192.168.1.10::5025::SOCKET");
        assertEquals("192.168.1.10", endpoint.host());
        assertEquals(5025, endpoint.port());

        VisaDeviceDriver.SocketEndpoint withBoard =
                VisaDeviceDriver.parseSocketResource("tcpip0::localhost::5025::socket");
        assertEquals("localhost", withBoard.host());
        assertEquals(5025, withBoard.port());
    }

    @Test
    void connectRejectsUnsupportedResource() {
        StubDriverObject object = new StubDriverObject(Map.of(
                "resource", "TCPIP0::127.0.0.1::inst0::INSTR"
        ));
        driver = new VisaDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("SOCKET"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new VisaDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("idn", "*IDN?")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeSocketInstrument implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-visa-socket-instrument");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicReference<String> voltage = new AtomicReference<>("0.0");

        FakeSocketInstrument() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void setVoltage(double volts) {
            voltage.set(Double.toString(volts));
        }

        String voltage() {
            return voltage.get();
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
                    String command = VisaDeviceDriver.readLine(in);
                    String upper = command.toUpperCase(Locale.ROOT);
                    if (upper.equals("*IDN?")) {
                        write(out, "ISPF,FakeSocketInstrument,1.0,SOCKET");
                    } else if (upper.equals("MEAS:VOLT:DC?")) {
                        write(out, voltage.get());
                    } else if (upper.startsWith("VOLT ") || upper.startsWith("MEAS:VOLT:DC ")) {
                        String[] parts = command.split("\s+", 2);
                        if (parts.length == 2) {
                            voltage.set(parts[1].trim());
                        }
                    } else if (upper.endsWith("?")) {
                        write(out, "");
                    }
                }
            } catch (IOException ignored) {
                // reset
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
                    "test-visa",
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
