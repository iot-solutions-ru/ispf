package com.ispf.driver.beckhoffads;

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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * Loopback tests for {@link BeckhoffAdsDeviceDriver} against a fake AMS/TCP ADS server.
 */
class BeckhoffAdsDeviceDriverTest {

    private BeckhoffAdsDeviceDriver driver;
    private FakeAdsServer adsServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (adsServer != null) {
            adsServer.close();
            adsServer = null;
        }
    }

    @Test
    void readsAndWritesTypedSymbolsViaLoopback() throws Exception {
        adsServer = new FakeAdsServer();
        adsServer.put(0xF020L, 0L, shortBytes((short) 1234));
        adsServer.put(0xF020L, 2L, intBytes(0x11223344));
        adsServer.put(0xF020L, 6L, floatBytes(3.5f));
        adsServer.put(0xF020L, 10L, stringBytes("hello", 16));
        adsServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(adsServer.port()),
                "targetAmsNetId", "192.168.0.1.1.1",
                "sourceAmsNetId", "127.0.0.1.1.1"
        ));
        driver = new BeckhoffAdsDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "intVal", "0xF020:0:INT",
                "dintVal", "0xF020:2:DINT",
                "realVal", "0xF020:6:REAL",
                "strVal", "0xF020:10:STRING:16"
        ));

        assertEquals("1234", object.variables.get("intVal").firstRow().get("value"));
        assertEquals(String.valueOf(0x11223344), object.variables.get("dintVal").firstRow().get("value"));
        assertEquals("3.5", object.variables.get("realVal").firstRow().get("value"));
        assertEquals("hello", object.variables.get("strVal").firstRow().get("value"));

        driver.writePoint("intVal", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 42)
        ));
        assertEquals(42, ByteBuffer.wrap(adsServer.get(0xF020L, 0L)).order(ByteOrder.LITTLE_ENDIAN).getShort());
        assertEquals("42", object.variables.get("intVal").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsFormats() {
        BeckhoffAdsPoint p = BeckhoffAdsPoint.parse("0xF020:0:INT");
        assertEquals(0xF020L, p.indexGroup());
        assertEquals(0L, p.indexOffset());
        assertEquals(BeckhoffAdsPoint.AdsValueType.INT, p.type());
        assertEquals(2, p.byteLength());

        BeckhoffAdsPoint s = BeckhoffAdsPoint.parse("16416:4:STRING:32");
        assertEquals(16416L, s.indexGroup());
        assertEquals(BeckhoffAdsPoint.AdsValueType.STRING, s.type());
        assertEquals(32, s.byteLength());
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new BeckhoffAdsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "0xF020:0:INT")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static byte[] shortBytes(short v) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array();
    }

    private static byte[] intBytes(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte[] floatBytes(float v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array();
    }

    private static byte[] stringBytes(String text, int capacity) {
        byte[] out = new byte[capacity];
        byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, out, 0, Math.min(raw.length, capacity - 1));
        return out;
    }

    private static final class FakeAdsServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ads-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, byte[]> memory = new ConcurrentHashMap<>();

        FakeAdsServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(long indexGroup, long indexOffset, byte[] data) {
            memory.put(key(indexGroup, indexOffset), Arrays.copyOf(data, data.length));
        }

        byte[] get(long indexGroup, long indexOffset) {
            return memory.getOrDefault(key(indexGroup, indexOffset), new byte[0]);
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        private static String key(long ig, long io) {
            return ig + ":" + io;
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
                    byte[] tcpHeader = new byte[6];
                    in.readFully(tcpHeader);
                    int length = (tcpHeader[2] & 0xFF)
                            | ((tcpHeader[3] & 0xFF) << 8)
                            | ((tcpHeader[4] & 0xFF) << 16)
                            | ((tcpHeader[5] & 0xFF) << 24);
                    byte[] ams = new byte[length];
                    in.readFully(ams);
                    out.write(buildResponse(ams));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        private byte[] buildResponse(byte[] requestAms) {
            ByteBuffer req = ByteBuffer.wrap(requestAms).order(ByteOrder.LITTLE_ENDIAN);
            byte[] targetNetId = new byte[6];
            req.get(targetNetId);
            int targetPort = req.getShort() & 0xFFFF;
            byte[] sourceNetId = new byte[6];
            req.get(sourceNetId);
            int sourcePort = req.getShort() & 0xFFFF;
            int commandId = req.getShort() & 0xFFFF;
            req.getShort(); // state
            int cbData = req.getInt();
            req.getInt(); // error
            int invoke = req.getInt();
            byte[] adsData = new byte[cbData];
            req.get(adsData);

            ByteBuffer adsReq = ByteBuffer.wrap(adsData).order(ByteOrder.LITTLE_ENDIAN);
            long indexGroup = adsReq.getInt() & 0xFFFFFFFFL;
            long indexOffset = adsReq.getInt() & 0xFFFFFFFFL;
            int dataLen = adsReq.getInt();

            byte[] responseAds;
            if (commandId == 0x0002) {
                byte[] stored = memory.getOrDefault(key(indexGroup, indexOffset), new byte[dataLen]);
                byte[] slice = Arrays.copyOf(stored, dataLen);
                responseAds = ByteBuffer.allocate(8 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(0)
                        .putInt(dataLen)
                        .put(slice)
                        .array();
            } else if (commandId == 0x0003) {
                byte[] payload = new byte[dataLen];
                adsReq.get(payload);
                memory.put(key(indexGroup, indexOffset), payload);
                responseAds = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array();
            } else {
                responseAds = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x701).array();
            }

            // Swap net ids / ports for response
            ByteBuffer ams = ByteBuffer.allocate(32 + responseAds.length).order(ByteOrder.LITTLE_ENDIAN);
            ams.put(sourceNetId);
            ams.putShort((short) sourcePort);
            ams.put(targetNetId);
            ams.putShort((short) targetPort);
            ams.putShort((short) commandId);
            ams.putShort((short) 0x0005);
            ams.putInt(responseAds.length);
            ams.putInt(0);
            ams.putInt(invoke);
            ams.put(responseAds);

            byte[] amsBytes = ams.array();
            ByteBuffer frame = ByteBuffer.allocate(6 + amsBytes.length).order(ByteOrder.LITTLE_ENDIAN);
            frame.putShort((short) 0);
            frame.putInt(amsBytes.length);
            frame.put(amsBytes);
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
                    "test-beckhoff-ads",
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
