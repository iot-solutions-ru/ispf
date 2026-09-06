package com.ispf.driver.iec101;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.iec101.codec.Iec101LabCodec;
import com.ispf.driver.iec101.codec.Iec101LabTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake TCP loopback tests for the IEC101-lab codec.
 */
class Iec101DeviceDriverTest {

    private Iec101DeviceDriver driver;
    private FakeIec101LabServer server;

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
    void metadataDescribesLabNotStub() {
        driver = new Iec101DeviceDriver();
        assertEquals("iec101", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase().contains("lab"));
        assertTrue(driver.metadata().description().contains("C_IC_NA_1"));
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
        server = new FakeIec101LabServer(1);
        server.putFloat(1001, 230.5f);
        server.putBool(42, true);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
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
    void writeSingleCommandUpdatesLabOutstation() throws Exception {
        server = new FakeIec101LabServer(1);
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
     * Minimal IEC101-lab outstation: STARTDT_CON + interrogation responses + command acks.
     */
    private static final class FakeIec101LabServer implements AutoCloseable {

        private static final byte[] STARTDT_CON = { 0x0B, 0x00, 0x00, 0x00 };

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-iec101-lab");
            thread.setDaemon(true);
            return thread;
        });
        private final int commonAddress;
        private final Map<Integer, Float> floats = new ConcurrentHashMap<>();
        private final Map<Integer, Boolean> bools = new ConcurrentHashMap<>();
        private final AtomicInteger sendSeq = new AtomicInteger();

        FakeIec101LabServer(int commonAddress) throws IOException {
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
                int recvSeq = 0;
                while (!socket.isClosed()) {
                    byte[] frame = readApdu(in);
                    Iec101LabCodec.ParsedApdu parsed = Iec101LabCodec.parseApdu(frame);
                    if (parsed.kind() == Iec101LabCodec.ApduKind.U) {
                        out.write(Iec101LabCodec.encodeUFrame(STARTDT_CON));
                        out.flush();
                        continue;
                    }
                    if (parsed.kind() != Iec101LabCodec.ApduKind.I) {
                        continue;
                    }
                    recvSeq = (recvSeq + 1) & 0x7FFF;
                    byte[] asdu = parsed.asdu();
                    int typeId = Iec101LabCodec.asduTypeId(asdu);
                    if (typeId == Iec101LabTypes.C_IC_NA_1) {
                        respondInterrogation(out, recvSeq);
                    } else if (typeId == Iec101LabTypes.C_SC_NA_1) {
                        for (var value : parsed.values()) {
                            bools.put(value.ioa(), value.bool());
                            byte[] ack = Iec101LabCodec.encodeAsdu(
                                    Iec101LabTypes.C_SC_NA_1,
                                    Iec101LabTypes.COT_ACTIVATION_CON,
                                    commonAddress,
                                    value.ioa(),
                                    new byte[] { (byte) (value.bool() ? 1 : 0) }
                            );
                            writeI(out, recvSeq, ack);
                        }
                    } else if (typeId == Iec101LabTypes.C_SE_NC_1) {
                        for (var value : parsed.values()) {
                            floats.put(value.ioa(), (float) value.numeric());
                            byte[] ack = Iec101LabCodec.encodeSetpointFloat(
                                    commonAddress, value.ioa(), (float) value.numeric());
                            // overwrite COT by re-encoding activation con style via measured path
                            writeI(out, recvSeq, Iec101LabCodec.encodeAsdu(
                                    Iec101LabTypes.C_SE_NC_1,
                                    Iec101LabTypes.COT_ACTIVATION_CON,
                                    commonAddress,
                                    value.ioa(),
                                    floatInfo((float) value.numeric())
                            ));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private void respondInterrogation(OutputStream out, int recvSeq) throws IOException {
            for (Map.Entry<Integer, Float> entry : new LinkedHashMap<>(floats).entrySet()) {
                writeI(out, recvSeq, Iec101LabCodec.encodeMeasuredFloat(
                        commonAddress, Iec101LabTypes.COT_INTERROGATED, entry.getKey(), entry.getValue(), 0));
            }
            for (Map.Entry<Integer, Boolean> entry : new LinkedHashMap<>(bools).entrySet()) {
                writeI(out, recvSeq, Iec101LabCodec.encodeSinglePoint(
                        commonAddress, Iec101LabTypes.COT_INTERROGATED, entry.getKey(), entry.getValue(), 0));
            }
            writeI(out, recvSeq, Iec101LabCodec.encodeAsdu(
                    Iec101LabTypes.C_IC_NA_1,
                    Iec101LabTypes.COT_ACTIVATION_CON,
                    commonAddress,
                    0,
                    new byte[] { 20 }
            ));
        }

        private void writeI(OutputStream out, int recvSeq, byte[] asdu) throws IOException {
            int seq = sendSeq.getAndIncrement() & 0x7FFF;
            out.write(Iec101LabCodec.encodeIFrame(seq, recvSeq, asdu));
            out.flush();
        }

        private static byte[] floatInfo(float value) throws IOException {
            java.io.ByteArrayOutputStream info = new java.io.ByteArrayOutputStream();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            buffer.putFloat(value);
            info.write(buffer.array());
            info.write(0);
            return info.toByteArray();
        }

        private static byte[] readApdu(InputStream in) throws IOException {
            int start = in.read();
            if (start < 0) {
                throw new EOFException();
            }
            int length = in.read();
            if (length < 0) {
                throw new EOFException();
            }
            byte[] body = in.readNBytes(length);
            if (body.length != length) {
                throw new EOFException();
            }
            byte[] frame = new byte[2 + length];
            frame[0] = (byte) start;
            frame[1] = (byte) length;
            System.arraycopy(body, 0, frame, 2, length);
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
