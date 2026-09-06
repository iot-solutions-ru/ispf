package com.ispf.driver.iec103;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.iec103.codec.Iec103LabCodec;
import com.ispf.driver.iec103.codec.Iec103LabTypes;
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
 * Fake TCP loopback tests for the IEC103-lab codec.
 */
class Iec103DeviceDriverTest {

    private Iec103DeviceDriver driver;
    private FakeIec103LabServer server;

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
        driver = new Iec103DeviceDriver();
        assertEquals("iec103", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase().contains("lab"));
        assertTrue(driver.metadata().description().contains("FUN/INF"));
    }

    @Test
    void pointParserAcceptsFunInfAndAsduForms() {
        assertEquals(new Iec103Point(1, 40, Iec103Point.Kind.MEASURED_FLOAT), Iec103Point.parse("1:40"));
        assertEquals(new Iec103Point(2, 16, Iec103Point.Kind.STATUS), Iec103Point.parse("1:2:16"));
        assertEquals(new Iec103Point(1, 40, Iec103Point.Kind.MEASURED_FLOAT), Iec103Point.parse("40:1:40"));
        assertEquals(new Iec103Point(1, 1, Iec103Point.Kind.MEASURANDS_II), Iec103Point.parse("9:1:1"));
        assertEquals(new Iec103Point(1, 40, Iec103Point.Kind.MEASURED_FLOAT),
                Iec103Point.parse("40:" + ((1 << 8) | 40)));
        assertEquals(new Iec103Point(2, 16, Iec103Point.Kind.STATUS), Iec103Point.parse("STATUS:2:16"));
    }

    @Test
    void interrogationReadsMeasuredAndStatus() throws Exception {
        server = new FakeIec103LabServer(1);
        server.putFloat(1, 40, 48.5f);
        server.putStatus(2, 16, true);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "commonAddress", "1",
                "timeoutMs", "2000"
        ));
        driver = new Iec103DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "current", "1:40",
                "trip", "STATUS:2:16"
        ));

        DataRecord current = object.variables.get("current");
        assertEquals(48.5, ((Number) current.firstRow().get("value")).doubleValue(), 0.001);
        assertEquals("GOOD", current.firstRow().get("quality"));
        assertEquals(1L, ((Number) current.firstRow().get("fun")).longValue());
        assertEquals(40L, ((Number) current.firstRow().get("inf")).longValue());

        DataRecord trip = object.variables.get("trip");
        assertEquals(true, trip.firstRow().get("value"));
        assertEquals(2L, ((Number) trip.firstRow().get("fun")).longValue());
        assertEquals(16L, ((Number) trip.firstRow().get("inf")).longValue());
    }

    @Test
    void writeGeneralCommandUpdatesLabOutstation() throws Exception {
        server = new FakeIec103LabServer(1);
        server.putStatus(2, 16, false);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "timeoutMs", "2000"
        ));
        driver = new Iec103DeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("trip", "1:2:16"));

        DataRecord command = DataRecord.single(
                DataSchema.builder("cmd").field("value", FieldType.BOOLEAN).build(),
                Map.of("value", true)
        );
        driver.writePoint("trip", command);
        assertEquals(true, object.variables.get("trip").firstRow().get("value"));
        assertEquals(true, server.status(2, 16));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new Iec103DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class,
                () -> driver.readPoints(Map.of("v", "1:40")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    /**
     * Minimal IEC103-lab outstation: STARTDT_CON + GI responses + command acks.
     */
    private static final class FakeIec103LabServer implements AutoCloseable {

        private static final byte[] STARTDT_CON = { 0x0B, 0x00, 0x00, 0x00 };

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-iec103-lab");
            thread.setDaemon(true);
            return thread;
        });
        private final int commonAddress;
        private final Map<Integer, Float> floats = new ConcurrentHashMap<>();
        private final Map<Integer, Boolean> status = new ConcurrentHashMap<>();
        private final AtomicInteger sendSeq = new AtomicInteger();

        FakeIec103LabServer(int commonAddress) throws IOException {
            this.commonAddress = commonAddress;
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putFloat(int fun, int inf, float value) {
            floats.put(pack(fun, inf), value);
        }

        void putStatus(int fun, int inf, boolean value) {
            status.put(pack(fun, inf), value);
        }

        boolean status(int fun, int inf) {
            return Boolean.TRUE.equals(status.get(pack(fun, inf)));
        }

        private static int pack(int fun, int inf) {
            return ((fun & 0xFF) << 8) | (inf & 0xFF);
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
                    Iec103LabCodec.ParsedApdu parsed = Iec103LabCodec.parseApdu(frame);
                    if (parsed.kind() == Iec103LabCodec.ApduKind.U) {
                        out.write(Iec103LabCodec.encodeUFrame(STARTDT_CON));
                        out.flush();
                        continue;
                    }
                    if (parsed.kind() != Iec103LabCodec.ApduKind.I) {
                        continue;
                    }
                    recvSeq = (recvSeq + 1) & 0x7FFF;
                    byte[] asdu = parsed.asdu();
                    int typeId = Iec103LabCodec.asduTypeId(asdu);
                    if (typeId == Iec103LabTypes.ASDU_GI) {
                        respondInterrogation(out, recvSeq);
                    } else if (typeId == Iec103LabTypes.ASDU_GENERAL_COMMAND) {
                        for (var value : parsed.values()) {
                            status.put(pack(value.fun(), value.inf()), value.bool());
                            byte[] ack = Iec103LabCodec.encodeAsdu(
                                    Iec103LabTypes.ASDU_GENERAL_COMMAND,
                                    Iec103LabTypes.COT_ACTIVATION_CON,
                                    commonAddress,
                                    value.fun(),
                                    value.inf(),
                                    new byte[] { (byte) (value.bool() ? 2 : 1) }
                            );
                            writeI(out, recvSeq, ack);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private void respondInterrogation(OutputStream out, int recvSeq) throws IOException {
            for (Map.Entry<Integer, Float> entry : new LinkedHashMap<>(floats).entrySet()) {
                int fun = (entry.getKey() >>> 8) & 0xFF;
                int inf = entry.getKey() & 0xFF;
                writeI(out, recvSeq, Iec103LabCodec.encodeLabMeasFloat(
                        commonAddress, Iec103LabTypes.COT_INTERROGATED, fun, inf, entry.getValue(), 0));
            }
            for (Map.Entry<Integer, Boolean> entry : new LinkedHashMap<>(status).entrySet()) {
                int fun = (entry.getKey() >>> 8) & 0xFF;
                int inf = entry.getKey() & 0xFF;
                writeI(out, recvSeq, Iec103LabCodec.encodeStatus(
                        commonAddress, Iec103LabTypes.COT_INTERROGATED, fun, inf, entry.getValue(), 0));
            }
            writeI(out, recvSeq, Iec103LabCodec.encodeAsdu(
                    Iec103LabTypes.ASDU_GI_TERMINATION,
                    Iec103LabTypes.COT_ACTIVATION_CON,
                    commonAddress,
                    0,
                    0,
                    new byte[] { 0 }
            ));
        }

        private void writeI(OutputStream out, int recvSeq, byte[] asdu) throws IOException {
            int seq = sendSeq.getAndIncrement() & 0x7FFF;
            out.write(Iec103LabCodec.encodeIFrame(seq, recvSeq, asdu));
            out.flush();
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
                    "test-iec103",
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
