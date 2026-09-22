package com.ispf.driver.iec101;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.iec101.codec.Iec101Codec;
import com.ispf.driver.iec101.codec.Iec101Frame;
import com.ispf.driver.iec101.codec.Iec101Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
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

class Iec101DeviceDriverTest {

    private Iec101DeviceDriver driver;
    private FakeIec101Outstation server;

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
    void metadataDescribesFt12Profile() {
        driver = new Iec101DeviceDriver();
        assertEquals("iec101", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("ft1.2"));
        assertTrue(description.contains("c_ic_na_1"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pointParserAcceptsIoaAndTypeForms() {
        assertEquals(new Iec101Point(1001, Iec101Point.Kind.MEASURED_FLOAT), Iec101Point.parse("1001"));
        assertEquals(new Iec101Point(1001, Iec101Point.Kind.MEASURED_FLOAT), Iec101Point.parse("M_ME_NC_1:1001"));
        assertEquals(new Iec101Point(42, Iec101Point.Kind.SINGLE_POINT), Iec101Point.parse("M_SP_NA_1:42"));
        assertEquals(new Iec101Point(7, Iec101Point.Kind.SINGLE_POINT), Iec101Point.parse("7:BOOL"));
        assertEquals(new Iec101Point(9, Iec101Point.Kind.MEASURED_FLOAT), Iec101Point.parse("9:FLOAT"));
    }

    @Test
    void interrogationReadsMeasuredAndSinglePoint() throws Exception {
        server = new FakeIec101Outstation(1);
        server.putFloat(1001, 230.5f);
        server.putBool(42, true);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "linkAddress", "1",
                "commonAddress", "1",
                "timeoutMs", "2000"
        ));
        driver = new Iec101DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "voltage", "M_ME_NC_1:1001",
                "breaker", "M_SP_NA_1:42"
        ));

        DataRecord voltage = object.variables.get("voltage");
        assertEquals(230.5, ((Number) voltage.firstRow().get("value")).doubleValue(), 0.001);
        assertEquals("GOOD", voltage.firstRow().get("quality"));
        assertEquals(1001L, ((Number) voltage.firstRow().get("ioa")).longValue());

        DataRecord breaker = object.variables.get("breaker");
        assertEquals(true, breaker.firstRow().get("value"));
        assertEquals(42L, ((Number) breaker.firstRow().get("ioa")).longValue());
    }

    @Test
    void writeSingleCommandUpdatesOutstation() throws Exception {
        server = new FakeIec101Outstation(1);
        server.putBool(42, false);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new Iec101DeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("breaker", "42:BOOL"));

        DataRecord command = DataRecord.single(
                DataSchema.builder("cmd").field("value", FieldType.BOOLEAN).build(),
                Map.of("value", true)
        );
        driver.writePoint("breaker", command);
        assertEquals(true, object.variables.get("breaker").firstRow().get("value"));
        assertEquals(true, server.bools().get(42));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new Iec101DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class,
                () -> driver.readPoints(Map.of("v", "1001")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    /**
     * Unbalanced outstation: ACK on reset-link, monitored points on interrogation, ACTCON on commands.
     */
    private static final class FakeIec101Outstation implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-iec101");
            thread.setDaemon(true);
            return thread;
        });
        private final int commonAddress;
        private final Map<Integer, Float> floats = new ConcurrentHashMap<>();
        private final Map<Integer, Boolean> bools = new ConcurrentHashMap<>();

        FakeIec101Outstation(int commonAddress) throws IOException {
            this.commonAddress = commonAddress;
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putFloat(int ioa, float value) {
            floats.put(ioa, value);
        }

        void putBool(int ioa, boolean value) {
            bools.put(ioa, value);
        }

        Map<Integer, Boolean> bools() {
            return bools;
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
                while (!socket.isClosed()) {
                    Iec101Frame frame = Iec101Codec.readFrame(in);
                    if (frame.kind() == Iec101Frame.Kind.FIXED
                            && Iec101Codec.functionCode(frame.control()) == Iec101Types.FC_RESET_REMOTE_LINK) {
                        out.write(Iec101Codec.encodeFixed(Iec101Types.FC_ACK, frame.linkAddress()));
                        out.flush();
                        continue;
                    }
                    if (frame.kind() != Iec101Frame.Kind.VARIABLE) {
                        continue;
                    }
                    byte[] asdu = frame.asdu();
                    int typeId = Iec101Codec.asduTypeId(asdu);
                    if (typeId == Iec101Types.C_IC_NA_1) {
                        respondInterrogation(out, frame.linkAddress());
                    } else if (typeId == Iec101Types.C_SC_NA_1) {
                        for (var value : Iec101Codec.decodeAsdu(asdu)) {
                            bools.put(value.ioa(), value.bool());
                            byte[] ack = Iec101Codec.encodeAsdu(
                                    Iec101Types.C_SC_NA_1,
                                    Iec101Types.COT_ACTIVATION_CON,
                                    commonAddress,
                                    value.ioa(),
                                    new byte[] { (byte) (value.bool() ? 1 : 0) }
                            );
                            writeVariable(out, frame.linkAddress(), ack);
                        }
                    } else if (typeId == Iec101Types.C_SE_NC_1) {
                        for (var value : Iec101Codec.decodeAsdu(asdu)) {
                            floats.put(value.ioa(), (float) value.numeric());
                            byte[] ack = Iec101Codec.encodeAsdu(
                                    Iec101Types.C_SE_NC_1,
                                    Iec101Types.COT_ACTIVATION_CON,
                                    commonAddress,
                                    value.ioa(),
                                    floatInfo((float) value.numeric())
                            );
                            writeVariable(out, frame.linkAddress(), ack);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private void respondInterrogation(OutputStream out, int linkAddress) throws IOException {
            for (Map.Entry<Integer, Float> entry : new LinkedHashMap<>(floats).entrySet()) {
                writeVariable(out, linkAddress, Iec101Codec.encodeMeasuredFloat(
                        commonAddress, Iec101Types.COT_INTERROGATED, entry.getKey(), entry.getValue(), 0));
            }
            for (Map.Entry<Integer, Boolean> entry : new LinkedHashMap<>(bools).entrySet()) {
                writeVariable(out, linkAddress, Iec101Codec.encodeSinglePoint(
                        commonAddress, Iec101Types.COT_INTERROGATED, entry.getKey(), entry.getValue(), 0));
            }
            writeVariable(out, linkAddress, Iec101Codec.encodeAsdu(
                    Iec101Types.C_IC_NA_1,
                    Iec101Types.COT_ACTIVATION_TERMINATION,
                    commonAddress,
                    0,
                    new byte[] { (byte) Iec101Types.QOI_STATION }
            ));
        }

        private static void writeVariable(OutputStream out, int linkAddress, byte[] asdu) throws IOException {
            int control = Iec101Types.FC_RESPOND_USER_DATA;
            out.write(Iec101Codec.encodeVariable(control, linkAddress, asdu));
            out.flush();
        }

        private static byte[] floatInfo(float value) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(5).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            buffer.putFloat(value);
            buffer.put((byte) 0);
            return buffer.array();
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
                    "test-iec101",
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
