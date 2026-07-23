package com.ispf.driver.mbus;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link MbusDeviceDriver} against a fake M-Bus TCP meter (EN 13757-3 framing)
 * on an ephemeral port. The fake answers jMBus REQ_UD2 short frames with a protocol-valid RSP_UD
 * long frame (CI=0x72, long header, no encryption) carrying a single 32-bit energy register.
 */
class MbusDeviceDriverTest {

    /** Raw register value transported by the fake meter (INT32, little-endian). */
    private static final long METER_ENERGY_RAW = 123456L;

    private MbusDeviceDriver driver;
    private FakeMbusMeter meter;
    private StubDriverObject driverObject;

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
    void readsEnergyRegisterViaLoopback() throws Exception {
        startMeter(true);
        driver = newDriver(meter.port());
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("energy", "1:12345678:energy"));

        DataRecord record = driverObject.variables.get("energy");
        assertEquals("123456", record.firstRow().get("value"));
        assertEquals("energy", record.firstRow().get("register"));
        assertEquals("WATT_HOUR", record.firstRow().get("unit"));

        // A mapping with secondaryAddress > 0 is read on the broadcast primary address 0xFD.
        assertEquals(0xFD, meter.lastRequestAddress());
    }

    @Test
    void primaryAddressMappingReadsPrimaryAddress() throws Exception {
        startMeter(true);
        driver = newDriver(meter.port());
        driver.connect();

        driver.readPoints(Map.of("energy", "7:0:energy"));

        DataRecord record = driverObject.variables.get("energy");
        assertEquals("123456", record.firstRow().get("value"));
        assertEquals(7, meter.lastRequestAddress());
    }

    @Test
    void unmatchedRegisterFallsBackToFirstRecord() throws Exception {
        startMeter(true);
        driver = newDriver(meter.port());
        driver.connect();

        driver.readPoints(Map.of("reading", "1:0:power"));

        DataRecord record = driverObject.variables.get("reading");
        assertEquals("123456", record.firstRow().get("value"));
        assertEquals("power", record.firstRow().get("register"));
        assertEquals("WATT_HOUR", record.firstRow().get("unit"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = newDriver(10001);

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("energy", "1:0:energy")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writeIsReadOnly() {
        driver = newDriver(10001);

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("energy", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void readFailsWhenMeterStaysSilent() throws Exception {
        startMeter(false);
        driver = newDriver(meter.port());
        driver.connect();

        // jMBus applies a bounded read timeout (500 ms default), so this fails fast.
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("energy", "1:0:energy")));
        assertTrue(error.getMessage().contains("M-Bus read failed"));
    }

    private MbusDeviceDriver newDriver(int port) {
        driverObject = new StubDriverObject(Map.of(
                "connectionType", "tcp",
                "host", "127.0.0.1",
                "port", String.valueOf(port)
        ));
        MbusDeviceDriver newDriver = new MbusDeviceDriver();
        newDriver.initialize(driverObject);
        return newDriver;
    }

    private void startMeter(boolean respond) throws IOException {
        meter = new FakeMbusMeter(respond);
        meter.start();
    }

    private static final class FakeMbusMeter implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final boolean respond;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-mbus-meter");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicInteger lastRequestAddress = new AtomicInteger(-1);

        FakeMbusMeter(boolean respond) throws IOException {
            this.respond = respond;
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int lastRequestAddress() {
            return lastRequestAddress.get();
        }

        void start() {
            executor.submit(this::serve);
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(10_000);
                    handle(socket);
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                    // keep accepting further driver connections
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                while (true) {
                    int first = in.read();
                    if (first == -1) {
                        return;
                    }
                    if (first == 0x10) {
                        // short frame: 0x10 C A CS 0x16 (SND_NKE / REQ_UD2)
                        byte[] rest = new byte[4];
                        in.readFully(rest);
                        int address = rest[1] & 0xFF;
                        lastRequestAddress.set(address);
                        if (respond) {
                            out.write(buildRspUd(address));
                            out.flush();
                        }
                    } else if (first == 0x68) {
                        // long frame: 0x68 L L 0x68 <L bytes> CS 0x16 — consume and ACK
                        int length = in.read() & 0xFF;
                        in.skipBytes(1 + 1 + length + 2);
                        out.write(0xE5);
                        out.flush();
                    } else {
                        return;
                    }
                }
            } catch (IOException ignored) {
                // client closed or timed out — back to the accept loop
            }
        }

        /**
         * Builds an RSP_UD frame (EN 13757-3): long header (CI=0x72, encryption mode NONE) and one
         * data record — DIF 0x04 (32-bit integer), VIF 0x00 (energy, 10^-3 Wh, description ENERGY).
         */
        private static byte[] buildRspUd(int address) {
            int[] user = {
                    0x08, address, 0x72,
                    0x78, 0x56, 0x34, 0x12,   // device id 12345678 (BCD, LSB first)
                    0x24, 0x40,               // manufacturer
                    0x01,                     // version
                    0x02,                     // device type: electricity
                    0x01,                     // access number
                    0x00,                     // status
                    0x00, 0x00,               // signature: 0 encrypted blocks, mode NONE
                    0x04,                     // DIF: 32-bit integer, instantaneous value
                    0x00,                     // VIF: energy, 10^-3 Wh
                    (int) (METER_ENERGY_RAW & 0xFF),
                    (int) ((METER_ENERGY_RAW >> 8) & 0xFF),
                    (int) ((METER_ENERGY_RAW >> 16) & 0xFF),
                    (int) ((METER_ENERGY_RAW >> 24) & 0xFF),
                    0x2F, 0x2F                // fill bytes
            };
            int length = user.length; // C + A + CI + user data
            byte[] frame = new byte[length + 6];
            frame[0] = 0x68;
            frame[1] = (byte) length;
            frame[2] = (byte) length;
            frame[3] = 0x68;
            int checksum = 0;
            for (int i = 0; i < user.length; i++) {
                frame[4 + i] = (byte) user[i];
                checksum += user[i];
            }
            frame[4 + user.length] = (byte) (checksum & 0xFF);
            frame[5 + user.length] = 0x16;
            return frame;
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
                    "test-mbus",
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
            // no-op
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
