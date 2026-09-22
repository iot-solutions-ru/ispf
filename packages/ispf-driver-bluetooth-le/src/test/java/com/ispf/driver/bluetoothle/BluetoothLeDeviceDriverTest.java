package com.ispf.driver.bluetoothle;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.bluetoothle.codec.H4HciCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
 * Fake TCP loopback tests for Bluetooth H4 HCI over TCP.
 * Certifies HCI Command framing only — not a BLE radio and not a full GATT client.
 */
class BluetoothLeDeviceDriverTest {

    private BluetoothLeDeviceDriver driver;
    private FakeH4HciPeer peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (peer != null) {
            peer.close();
            peer = null;
        }
    }

    @Test
    void hciResetAndReadBdAddrAreHandwrittenH4Literals() {
        // Handwritten published H4 HCI_Reset: packet type 0x01, opcode 0x0C03 LE, length 0.
        byte[] resetExpected = new byte[] { 0x01, 0x03, 0x0C, 0x00 };
        // Handwritten published H4 HCI_Read_BD_ADDR: opcode 0x1009 LE.
        byte[] readBdExpected = new byte[] { 0x01, 0x09, 0x10, 0x00 };

        assertArrayEquals(resetExpected, H4HciCodec.encodeReset());
        assertArrayEquals(resetExpected, BluetoothLeDeviceDriver.encodeResetCommand());
        assertArrayEquals(readBdExpected, H4HciCodec.encodeReadBdAddr());
        assertArrayEquals(readBdExpected, BluetoothLeDeviceDriver.encodeReadBdAddrCommand());
    }

    @Test
    void metadataIsProductionH4Hci() {
        driver = new BluetoothLeDeviceDriver();
        assertEquals("bluetooth-le", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        assertEquals("9999", driver.metadata().configurationSchema().get("port"));
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("h4") || description.contains("hci"));
        assertTrue(description.contains("not") && description.contains("ble radio"));
        assertTrue(description.contains("not") && description.contains("gatt"));
        assertFalse(description.contains("lab"));
        assertTrue(!description.contains("stub") && !description.contains("placeholder"));
    }

    @Test
    void pointParserAcceptsBdAddr() throws Exception {
        BluetoothLePoint point = BluetoothLePoint.parse("hci:bd_addr");
        assertEquals(BluetoothLePoint.Kind.BD_ADDR, point.kind());
        assertEquals("bd_addr", point.display());
        assertFalse(point.writable());
        assertEquals(BluetoothLePoint.Kind.BD_ADDR, BluetoothLePoint.parse("bd_addr").kind());
    }

    @Test
    void connectResetAndReadBdAddrLoopback() throws Exception {
        peer = new FakeH4HciPeer(new byte[] {
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66
        });
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new BluetoothLeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertArrayEquals(new byte[] { 0x01, 0x03, 0x0C, 0x00 }, peer.lastResetCommand());

        driver.readPoints(Map.of("addr", "bd_addr"));
        assertEquals("66:55:44:33:22:11", object.variables.get("addr").firstRow().get("value"));
        assertArrayEquals(new byte[] { 0x01, 0x09, 0x10, 0x00 }, peer.lastReadBdCommand());
    }

    @Test
    void writeRejectedNotGattClient() throws Exception {
        peer = new FakeH4HciPeer(new byte[] {
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF
        });
        peer.start();
        assertTrue(peer.awaitReady(2, TimeUnit.SECONDS));

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new BluetoothLeDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("addr", "bd_addr"));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("addr", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.STRING).build(),
                        Map.of("value", "00:11:22:33:44:55")
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("gatt"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new BluetoothLeDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("addr", "bd_addr")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    /**
     * In-process H4 HCI peer: replies to Reset with Command Complete
     * {@code 04 0E 04 01 03 0C 00}, and to Read_BD_ADDR with status 0 + BD_ADDR.
     */
    private static final class FakeH4HciPeer implements AutoCloseable {

        private static final byte[] RESET_CMD = new byte[] { 0x01, 0x03, 0x0C, 0x00 };
        private static final byte[] READ_BD_CMD = new byte[] { 0x01, 0x09, 0x10, 0x00 };
        private static final byte[] RESET_COMPLETE = new byte[] {
                0x04, 0x0E, 0x04, 0x01, 0x03, 0x0C, 0x00
        };

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-h4-hci");
            thread.setDaemon(true);
            return thread;
        });
        private final byte[] bdAddrControllerOrder;
        private final CountDownLatch ready = new CountDownLatch(1);
        private final AtomicReference<byte[]> lastReset = new AtomicReference<>();
        private final AtomicReference<byte[]> lastReadBd = new AtomicReference<>();

        FakeH4HciPeer(byte[] bdAddrControllerOrder) throws IOException {
            if (bdAddrControllerOrder == null || bdAddrControllerOrder.length != 6) {
                throw new IllegalArgumentException("BD_ADDR must be 6 octets");
            }
            this.bdAddrControllerOrder = bdAddrControllerOrder.clone();
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        byte[] lastResetCommand() {
            return lastReset.get();
        }

        byte[] lastReadBdCommand() {
            return lastReadBd.get();
        }

        void start() {
            var unusedAccept = executor.submit(this::acceptLoop);
            ready.countDown();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    var unusedHandle = executor.submit(() -> handle(socket));
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
                    byte[] command = readCommand(in);
                    if (command == null) {
                        return;
                    }
                    if (Arrays.equals(command, RESET_CMD)) {
                        lastReset.set(command.clone());
                        out.write(RESET_COMPLETE);
                        out.flush();
                    } else if (Arrays.equals(command, READ_BD_CMD)) {
                        lastReadBd.set(command.clone());
                        out.write(readBdAddrComplete());
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // client closed
            }
        }

        private byte[] readBdAddrComplete() {
            byte[] event = new byte[13];
            event[0] = 0x04;
            event[1] = 0x0E;
            event[2] = 0x0A;
            event[3] = 0x01;
            event[4] = 0x09;
            event[5] = 0x10;
            event[6] = 0x00;
            System.arraycopy(bdAddrControllerOrder, 0, event, 7, 6);
            return event;
        }

        private static byte[] readCommand(InputStream in) throws IOException {
            int type = in.read();
            if (type < 0) {
                return null;
            }
            int opLo = in.read();
            int opHi = in.read();
            int length = in.read();
            if (opLo < 0 || opHi < 0 || length < 0) {
                return null;
            }
            byte[] params = new byte[length];
            int offset = 0;
            while (offset < length) {
                int n = in.read(params, offset, length - offset);
                if (n < 0) {
                    return null;
                }
                offset += n;
            }
            byte[] command = new byte[4 + length];
            command[0] = (byte) type;
            command[1] = (byte) opLo;
            command[2] = (byte) opHi;
            command[3] = (byte) length;
            System.arraycopy(params, 0, command, 4, length);
            return command;
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
                    "test-bluetooth-le",
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
