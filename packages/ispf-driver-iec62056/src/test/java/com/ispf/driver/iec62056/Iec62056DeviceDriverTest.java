package com.ispf.driver.iec62056;

import com.ispf.core.model.DataRecord;
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
import java.util.LinkedHashMap;
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
 * Loopback tests for {@link Iec62056DeviceDriver} against a fake IEC 62056-21 Mode C meter.
 */
class Iec62056DeviceDriverTest {

    private Iec62056DeviceDriver driver;
    private FakeModeCMeterServer meter;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (meter != null) {
            meter.close();
            meter = null;
        }
    }

    @Test
    void metadataDescribesModeCCompanionNotStub() {
        driver = new Iec62056DeviceDriver();
        assertEquals("iec62056", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().contains("62056-21"));
        assertTrue(driver.metadata().description().toLowerCase().contains("mode c"));
    }

    @Test
    void readsObisValuesViaModeCLoopback() throws Exception {
        meter = new FakeModeCMeterServer(Map.of(
                "1.8.0", "001234.56*kWh",
                "1-0:1.8.1", "000100.00*kWh"
        ));
        meter.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(meter.port()),
                "timeoutMs", "2000",
                "baudId", "5"
        ));
        driver = new Iec62056DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "energy", "1.8.0",
                "tariff1", "1.8.1"
        ));

        DataRecord energy = object.variables.get("energy");
        assertEquals("001234.56", energy.firstRow().get("value"));
        assertEquals("kWh", energy.firstRow().get("unit"));
        assertEquals("1.8.0", energy.firstRow().get("obis"));
        assertTrue(String.valueOf(energy.firstRow().get("identification")).startsWith("/"));

        DataRecord tariff = object.variables.get("tariff1");
        assertEquals("000100.00", tariff.firstRow().get("value"));
        assertEquals("kWh", tariff.firstRow().get("unit"));
    }

    @Test
    void pointParserAcceptsObisFormats() {
        assertEquals(new Iec62056Point("1.8.0"), Iec62056Point.parse("1.8.0"));
        assertEquals(new Iec62056Point("1-0:1.8.0"), Iec62056Point.parse("1-0:1.8.0"));
        assertEquals(new Iec62056Point("1-0:1.8.0*255"), Iec62056Point.parse("1-0:1.8.0*255"));
        assertTrue(Iec62056Point.parse("1.8.1").matchesLineObis("1-0:1.8.1*255"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new Iec62056DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("energy", "1.8.0")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writePointIsReadoutOnly() throws Exception {
        meter = new FakeModeCMeterServer(Map.of("1.8.0", "1*kWh"));
        meter.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(meter.port())
        ));
        driver = new Iec62056DeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("energy", "1.8.0"));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("energy", object.variables.get("energy")));
        assertTrue(error.getMessage().toLowerCase().contains("readout-only"));
    }

    private static final class FakeModeCMeterServer implements AutoCloseable {

        private static final byte ACK = 0x06;
        private static final byte STX = 0x02;
        private static final byte ETX = 0x03;
        private static final byte CR = 0x0D;
        private static final byte LF = 0x0A;

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-iec62056-mode-c");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> registers;

        FakeModeCMeterServer(Map<String, String> registers) throws IOException {
            this.registers = new LinkedHashMap<>(registers);
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
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

                String signOn = readLine(in);
                if (!signOn.startsWith("/?") || !signOn.endsWith("!")) {
                    return;
                }

                out.write("/ISPF5TESTMETER\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();

                byte[] ack = in.readNBytes(6);
                if (ack.length < 6 || ack[0] != ACK) {
                    return;
                }

                out.write(buildDataBlock());
                out.flush();
            } catch (IOException ignored) {
            }
        }

        private byte[] buildDataBlock() {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            for (Map.Entry<String, String> entry : registers.entrySet()) {
                String line = entry.getKey() + "(" + entry.getValue() + ")\r\n";
                body.writeBytes(line.getBytes(StandardCharsets.US_ASCII));
            }
            body.writeBytes("!\r\n".getBytes(StandardCharsets.US_ASCII));
            body.write(ETX);

            byte[] payload = body.toByteArray();
            byte bcc = 0;
            for (byte value : payload) {
                bcc ^= value;
            }

            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(STX);
            frame.writeBytes(payload);
            frame.write(bcc);
            return frame.toByteArray();
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int prev = -1;
            int b;
            while ((b = in.read()) >= 0) {
                if (b == LF && prev == CR) {
                    break;
                }
                if (b != CR && b != LF) {
                    buffer.write(b);
                }
                prev = b;
            }
            return buffer.toString(StandardCharsets.US_ASCII);
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
                    "test-iec62056",
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
