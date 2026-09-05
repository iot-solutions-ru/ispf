package com.ispf.driver.bacnetmstp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.bacnetmstp.codec.BacnetMstpLabCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
 * Fake TCP loopback tests for the BACnet MS/TP gateway lab codec.
 */
class BacnetMstpDeviceDriverTest {

    private BacnetMstpDeviceDriver driver;
    private FakeBacnetMstpGateway gateway;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (gateway != null) {
            gateway.close();
            gateway = null;
        }
    }

    @Test
    void metadataDescribesGatewayLabNotNativeMstp() {
        driver = new BacnetMstpDeviceDriver();
        assertEquals("bacnet-mstp", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase();
        assertTrue(description.contains("lab") || description.contains("gateway"));
        assertTrue(description.contains("not native") || description.contains("not a native"));
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
    void readAiPresentValueAndWriteAv() throws Exception {
        gateway = new FakeBacnetMstpGateway();
        gateway.put(objectId(0, 1), 18.75f);
        gateway.put(objectId(2, 3), 1.0f);
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
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
        assertEquals(42.5f, gateway.get(objectId(2, 3)), 0.001f);
        assertEquals(42.5, (Double) object.variables.get("av3").firstRow().get("value"), 0.001);
    }

    @Test
    void writeToAnalogInputRejected() throws Exception {
        gateway = new FakeBacnetMstpGateway();
        gateway.put(objectId(0, 1), 5f);
        gateway.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(gateway.port()),
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
        assertTrue(error.getMessage().toLowerCase().contains("read-only"));
    }

    private static int objectId(int type, int instance) {
        return ((type & 0x3FF) << 22) | (instance & 0x3FFFFF);
    }

    private static final class FakeBacnetMstpGateway implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-bacnet-mstp");
            t.setDaemon(true);
            return t;
        });
        private final Map<Integer, Float> values = new ConcurrentHashMap<>();

        FakeBacnetMstpGateway() throws IOException {
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
                    byte[] frame = readFrame(in);
                    BacnetMstpLabCodec.Message message = BacnetMstpLabCodec.decode(frame);
                    if (message instanceof BacnetMstpLabCodec.ReadPropertyRequest request) {
                        float value = values.getOrDefault(request.objectId(), 0f);
                        out.write(BacnetMstpLabCodec.encodeReadPropertyAck(
                                request.invokeId(), request.objectId(), request.propertyId(), value));
                        out.flush();
                    } else if (message instanceof BacnetMstpLabCodec.WritePropertyRequest request) {
                        values.put(request.objectId(), request.value());
                        out.write(BacnetMstpLabCodec.encodeSimpleAck(
                                request.invokeId(), BacnetMstpLabCodec.SERVICE_WRITE_PROPERTY));
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }

        private static byte[] readFrame(InputStream in) throws IOException {
            byte[] header = readFully(in, 2);
            int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
            byte[] body = readFully(in, length);
            byte[] frame = new byte[2 + length];
            System.arraycopy(header, 0, frame, 0, 2);
            System.arraycopy(body, 0, frame, 2, length);
            return frame;
        }

        private static byte[] readFully(InputStream in, int length) throws IOException {
            byte[] buffer = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = in.read(buffer, offset, length - offset);
                if (read < 0) {
                    throw new EOFException();
                }
                offset += read;
            }
            return buffer;
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
