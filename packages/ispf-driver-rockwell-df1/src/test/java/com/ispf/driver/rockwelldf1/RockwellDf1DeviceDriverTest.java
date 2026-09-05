package com.ispf.driver.rockwelldf1;

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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
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
 * Loopback tests for {@link RockwellDf1DeviceDriver} against a fake DF1 TCP-bridge server.
 */
class RockwellDf1DeviceDriverTest {

    private RockwellDf1DeviceDriver driver;
    private FakeDf1Server server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void metadataIsProductionReadWrite() {
        RockwellDf1DeviceDriver underTest = new RockwellDf1DeviceDriver();
        assertEquals("rockwell-df1", underTest.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, underTest.metadata().maturity());
        assertEquals(Set.of("read", "write"), underTest.metadata().capabilities());
    }

    @Test
    void readsAndWritesTypedFilesViaLoopback() throws Exception {
        server = new FakeDf1Server();
        server.putInt("N7:0", 1234);
        server.putFloat("F8:1", 3.5f);
        server.putInt("B3:0", 0x0005); // bit 0 and bit 2 set
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port())
        ));
        driver = new RockwellDf1DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "n", "N7:0",
                "f", "F8:1",
                "b", "B3:0/0"
        ));

        assertEquals("1234", object.variables.get("n").firstRow().get("value"));
        assertEquals("N7", object.variables.get("n").firstRow().get("file"));
        assertEquals("3.5", object.variables.get("f").firstRow().get("value"));
        assertEquals("1", object.variables.get("b").firstRow().get("value"));

        driver.writePoint("n", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 42)
        ));
        assertEquals(42, server.getInt("N7:0"));
        assertEquals("42", object.variables.get("n").firstRow().get("value"));

        driver.writePoint("f", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.FLOAT).build(),
                Map.of("value", 9.25f)
        ));
        assertEquals(9.25f, server.getFloat("F8:1"), 0.001f);
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(
                new RockwellDf1Point(RockwellDf1Point.FileType.N, 7, 0, 0),
                RockwellDf1Point.parse("N7:0")
        );
        assertEquals(
                new RockwellDf1Point(RockwellDf1Point.FileType.F, 8, 1, 0),
                RockwellDf1Point.parse("F8:1")
        );
        assertEquals(
                new RockwellDf1Point(RockwellDf1Point.FileType.B, 3, 0, 0),
                RockwellDf1Point.parse("B3:0/0")
        );
        assertEquals(
                new RockwellDf1Point(RockwellDf1Point.FileType.B, 3, 0, 2),
                RockwellDf1Point.parse("b3:0/2")
        );
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new RockwellDf1DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "N7:0")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeDf1Server implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-df1-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

        FakeDf1Server() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putInt(String key, int value) {
            storage.put(key, RockwellDf1Frame.encodeInt16(value));
        }

        void putFloat(String key, float value) {
            storage.put(key, RockwellDf1Frame.encodeFloat(value));
        }

        int getInt(String key) {
            return RockwellDf1Frame.decodeInt16(storage.getOrDefault(key, new byte[2]));
        }

        float getFloat(String key) {
            return RockwellDf1Frame.decodeFloat(storage.getOrDefault(key, new byte[4]));
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
                    byte[] pdu = RockwellDf1Frame.readFrame(in);
                    out.write(buildReply(pdu));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException | RuntimeException ignored) {
            }
        }

        private byte[] buildReply(byte[] requestPdu) {
            RockwellDf1Frame.ParsedPdu req = RockwellDf1Frame.parsePdu(requestPdu);
            if (req.cmd() != RockwellDf1Frame.CMD_PROTECTED) {
                return RockwellDf1Frame.buildReply(req.src(), req.dst(), req.tns(), (byte) 0x10, new byte[0]);
            }
            byte[] payload = req.payload();
            RockwellDf1Point point = RockwellDf1Frame.parseAddress(payload);
            String key = storageKey(point);
            if (req.fnc() == RockwellDf1Frame.FNC_TYPED_READ) {
                int size = payload.length > 4 ? (payload[4] & 0xFF) : RockwellDf1Frame.elementSize(point);
                byte[] data = storage.getOrDefault(key, new byte[size]);
                if (data.length < size) {
                    data = Arrays.copyOf(data, size);
                } else if (data.length > size) {
                    data = Arrays.copyOf(data, size);
                }
                return RockwellDf1Frame.buildReply(req.src(), req.dst(), req.tns(), RockwellDf1Frame.STS_OK, data);
            }
            if (req.fnc() == RockwellDf1Frame.FNC_TYPED_WRITE) {
                int size = payload.length > 4 ? (payload[4] & 0xFF) : 0;
                byte[] data = Arrays.copyOfRange(payload, 5, 5 + size);
                storage.put(key, data);
                return RockwellDf1Frame.buildReply(req.src(), req.dst(), req.tns(), RockwellDf1Frame.STS_OK, new byte[0]);
            }
            return RockwellDf1Frame.buildReply(req.src(), req.dst(), req.tns(), (byte) 0x10, new byte[0]);
        }

        private static String storageKey(RockwellDf1Point point) {
            // Store whole elements; bit addressing is display-only for B reads
            return point.fileType().token() + point.fileNumber() + ":" + point.element();
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
                    "test-rockwell-df1",
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
