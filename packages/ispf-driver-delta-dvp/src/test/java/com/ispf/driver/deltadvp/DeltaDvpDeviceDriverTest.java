package com.ispf.driver.deltadvp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link DeltaDvpDeviceDriver} against a fake Modbus TCP server.
 */
class DeltaDvpDeviceDriverTest {

    private DeltaDvpDeviceDriver driver;
    private FakeModbusTcpServer modbusServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (modbusServer != null) {
            modbusServer.close();
            modbusServer = null;
        }
    }

    @Test
    void readsAndWritesHoldingRegistersViaLoopback() throws Exception {
        modbusServer = new FakeModbusTcpServer();
        modbusServer.put(100, 0x1234);
        modbusServer.put(101, 0x00AB);
        modbusServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(modbusServer.port()),
                "unitId", "1"
        ));
        driver = new DeltaDvpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals("delta-dvp", driver.metadata().id());

        driver.readPoints(Map.of(
                "pair", "HR:100:2",
                "single", "D100"
        ));

        assertEquals("4660,171", object.variables.get("pair").firstRow().get("value"));
        assertEquals("4660", object.variables.get("single").firstRow().get("value"));

        driver.writePoint("single", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 42)
        ));
        assertEquals(42, modbusServer.get(100));
        assertEquals("42", object.variables.get("single").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(new DeltaDvpPoint(100, 1), DeltaDvpPoint.parse("HR:100"));
        assertEquals(new DeltaDvpPoint(100, 1), DeltaDvpPoint.parse("100"));
        assertEquals(new DeltaDvpPoint(100, 1), DeltaDvpPoint.parse("D100"));
        assertEquals(new DeltaDvpPoint(100, 1), DeltaDvpPoint.parse("D:100"));
        assertEquals(new DeltaDvpPoint(100, 3), DeltaDvpPoint.parse("HR:100:3"));
        assertEquals(new DeltaDvpPoint(100, 2), DeltaDvpPoint.parse("D:100:2"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new DeltaDvpDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "HR:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeModbusTcpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-delta-dvp-modbus");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, Integer> holding = new ConcurrentHashMap<>();

        FakeModbusTcpServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int address, int value) {
            holding.put(address, value & 0xFFFF);
        }

        int get(int address) {
            return holding.getOrDefault(address, 0);
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
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                while (true) {
                    byte[] header = new byte[7];
                    in.readFully(header);
                    int length = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
                    byte[] pdu = new byte[length - 1];
                    in.readFully(pdu);
                    out.write(buildResponse(header, pdu));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        private byte[] buildResponse(byte[] header, byte[] pdu) {
            byte unitId = header[6];
            byte function = pdu[0];
            byte[] responsePdu;
            if (function == 3) {
                int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
                int count = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
                ByteBuffer buf = ByteBuffer.allocate(2 + count * 2);
                buf.put(function);
                buf.put((byte) (count * 2));
                for (int i = 0; i < count; i++) {
                    int word = holding.getOrDefault(address + i, 0);
                    buf.putShort((short) word);
                }
                responsePdu = buf.array();
            } else if (function == 6) {
                responsePdu = pdu.clone();
                int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
                int word = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
                holding.put(address, word);
            } else {
                responsePdu = new byte[] { (byte) (function | 0x80), 0x01 };
            }

            ByteBuffer frame = ByteBuffer.allocate(7 + responsePdu.length);
            frame.put(header, 0, 4);
            frame.putShort((short) (1 + responsePdu.length));
            frame.put(unitId);
            frame.put(responsePdu);
            return frame.array();
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
                    "test-delta-dvp",
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
