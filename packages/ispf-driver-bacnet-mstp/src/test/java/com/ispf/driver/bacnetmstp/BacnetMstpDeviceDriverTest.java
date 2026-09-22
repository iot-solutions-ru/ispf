package com.ispf.driver.bacnetmstp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.bacnetmstp.codec.BacnetMstpCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacnetMstpDeviceDriverTest {

    private BacnetMstpDeviceDriver driver;
    private FakeBacnetMstpPeer peer;

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
    void metadataDescribesClause9NotNativeMstp() {
        driver = new BacnetMstpDeviceDriver();
        assertEquals("bacnet-mstp", driver.metadata().id());
        assertEquals(DriverMaturity.BETA, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("clause") || description.contains("ms/tp"));
        assertTrue(description.contains("not native") || description.contains("not a native"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsAiCommaAndColonForms() throws Exception {
        assertEquals(BacnetMstpPoint.ObjectType.ANALOG_INPUT, BacnetMstpPoint.parse("analog-input,1").objectType());
        assertEquals(1, BacnetMstpPoint.parse("analog-input,1").instance());
        assertEquals(BacnetMstpPoint.ObjectType.ANALOG_INPUT, BacnetMstpPoint.parse("AI:1").objectType());
        assertEquals(BacnetMstpPoint.ObjectType.ANALOG_OUTPUT, BacnetMstpPoint.parse("AO:2").objectType());
        assertEquals(BacnetMstpPoint.ObjectType.ANALOG_VALUE, BacnetMstpPoint.parse("AV:3").objectType());
    }

    @Test
    void frameStartsWithPreambleAndCorruptHeaderCrcFails() {
        byte[] frame = BacnetMstpCodec.encodeReadProperty(1, 0, 1, objectId(0, 1), BacnetMstpCodec.PRESENT_VALUE);
        assertEquals(0x55, frame[0] & 0xFF);
        assertEquals(0xFF, frame[1] & 0xFF);
        assertEquals(BacnetMstpCodec.FRAME_TYPE_BACNET_DATA_EXPECTING_REPLY, frame[2] & 0xFF);
        BacnetMstpCodec.Message decoded = BacnetMstpCodec.decode(frame);
        assertTrue(decoded instanceof BacnetMstpCodec.ReadPropertyRequest);

        byte[] corrupt = frame.clone();
        corrupt[7] = (byte) (corrupt[7] ^ 0xFF);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> BacnetMstpCodec.decode(corrupt));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("header crc"));
    }

    @Test
    void readAiPresentValueAndWriteAv() throws Exception {
        peer = new FakeBacnetMstpPeer();
        peer.put(objectId(0, 1), 18.75f);
        peer.put(objectId(2, 3), 1.0f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "localMac", "0",
                "remoteMac", "1",
                "timeoutMs", "2000"
        ));
        driver = new BacnetMstpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "ai1", "analog-input,1",
                "av3", "AV:3"
        ));
        assertEquals(18.75, (Double) object.variables.get("ai1").firstRow().get("value"), 0.001);
        assertEquals(1.0, (Double) object.variables.get("av3").firstRow().get("value"), 0.001);

        driver.writePoint("av3", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 42.5)
        ));
        assertEquals(42.5f, peer.get(objectId(2, 3)), 0.001f);
        assertEquals(42.5, (Double) object.variables.get("av3").firstRow().get("value"), 0.001);
    }

    @Test
    void writeToAnalogInputRejected() throws Exception {
        peer = new FakeBacnetMstpPeer();
        peer.put(objectId(0, 1), 5f);
        peer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "timeoutMs", "2000"
        ));
        driver = new BacnetMstpDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("ai1", "AI:1"));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("ai1", DataRecord.single(
                        DataSchema.builder("v").field("value", FieldType.DOUBLE).build(),
                        Map.of("value", 9.0)
                )));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("rejects writes"));
    }

    private static int objectId(int type, int instance) {
        return ((type & 0x3FF) << 22) | (instance & 0x3FFFFF);
    }

    private static final class FakeBacnetMstpPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-bacnet-mstp");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Float> values = new ConcurrentHashMap<>();

        FakeBacnetMstpPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(int objectId, float value) {
            values.put(objectId, value);
        }

        float get(int objectId) {
            return values.getOrDefault(objectId, 0f);
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
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    byte[] frame = BacnetMstpCodec.readFrame(in);
                    BacnetMstpCodec.Message message = BacnetMstpCodec.decode(frame);
                    if (message instanceof BacnetMstpCodec.ReadPropertyRequest request) {
                        float value = values.getOrDefault(request.objectId(), 0f);
                        out.write(BacnetMstpCodec.encodeReadPropertyAck(
                                /* destination= */ request.source(),
                                /* source= */ request.destination(),
                                request.invokeId(),
                                request.objectId(),
                                request.propertyId(),
                                value));
                        out.flush();
                    } else if (message instanceof BacnetMstpCodec.WritePropertyRequest request) {
                        values.put(request.objectId(), request.value());
                        out.write(BacnetMstpCodec.encodeSimpleAck(
                                /* destination= */ request.source(),
                                /* source= */ request.destination(),
                                request.invokeId(),
                                BacnetMstpCodec.SERVICE_WRITE_PROPERTY));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // closed
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
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-bacnet-mstp", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
