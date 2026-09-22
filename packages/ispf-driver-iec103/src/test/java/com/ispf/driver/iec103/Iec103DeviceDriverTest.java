package com.ispf.driver.iec103;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.iec103.codec.Iec103Codec;
import com.ispf.driver.iec103.codec.Iec103Frame;
import com.ispf.driver.iec103.codec.Iec103Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
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

/**
 * In-process FT1.2 outstation loopback tests for IEC 60870-5-103.
 */
class Iec103DeviceDriverTest {

    private Iec103DeviceDriver driver;
    private FakeIec103Outstation server;

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
    void metadataDescribesFt12NotLab() {
        driver = new Iec103DeviceDriver();
        assertEquals("iec103", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("ft1.2"));
        assertTrue(driver.metadata().description().contains("FUN/INF"));
        assertFalse(description.contains("lab"));
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
        server = new FakeIec103Outstation(1);
        server.putFloat(1, 40, 48.5f);
        server.putStatus(2, 16, true);
        server.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "linkAddress", "1",
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
    void writeGeneralCommandUpdatesOutstation() throws Exception {
        server = new FakeIec103Outstation(1);
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
     * FT1.2 outstation: ACK on reset-link, monitored points on GI, ACTCON on general command.
     */
    private static final class FakeIec103Outstation implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-iec103");
            thread.setDaemon(true);
            return thread;
        });
        private final int asduAddress;
        private final Map<Integer, Float> floats = new ConcurrentHashMap<>();
        private final Map<Integer, Boolean> status = new ConcurrentHashMap<>();

        FakeIec103Outstation(int asduAddress) throws IOException {
            this.asduAddress = asduAddress;
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
                while (!socket.isClosed()) {
                    Iec103Frame frame = Iec103Codec.readFrame(in);
                    if (frame.kind() == Iec103Frame.Kind.FIXED
                            && Iec103Codec.functionCode(frame.control()) == Iec103Types.FC_RESET_REMOTE_LINK) {
                        out.write(Iec103Codec.encodeFixed(Iec103Types.FC_ACK, frame.linkAddress()));
                        out.flush();
                        continue;
                    }
                    if (frame.kind() != Iec103Frame.Kind.VARIABLE) {
                        continue;
                    }
                    byte[] asdu = frame.asdu();
                    int typeId = Iec103Codec.asduTypeId(asdu);
                    if (typeId == Iec103Types.ASDU_GI) {
                        respondInterrogation(out, frame.linkAddress());
                    } else if (typeId == Iec103Types.ASDU_GENERAL_COMMAND) {
                        for (var value : Iec103Codec.decodeAsdu(asdu)) {
                            status.put(pack(value.fun(), value.inf()), value.bool());
                            byte[] ack = Iec103Codec.encodeAsdu(
                                    Iec103Types.ASDU_GENERAL_COMMAND,
                                    Iec103Types.COT_COMMAND_ACK,
                                    asduAddress,
                                    value.fun(),
                                    value.inf(),
                                    new byte[] { (byte) (value.bool() ? 2 : 1) }
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
                int fun = (entry.getKey() >>> 8) & 0xFF;
                int inf = entry.getKey() & 0xFF;
                writeVariable(out, linkAddress, Iec103Codec.encodeMeasFloat(
                        asduAddress, Iec103Types.COT_GENERAL_INTERROGATION, fun, inf, entry.getValue(), 0));
            }
            for (Map.Entry<Integer, Boolean> entry : new LinkedHashMap<>(status).entrySet()) {
                int fun = (entry.getKey() >>> 8) & 0xFF;
                int inf = entry.getKey() & 0xFF;
                writeVariable(out, linkAddress, Iec103Codec.encodeStatus(
                        asduAddress, Iec103Types.COT_GENERAL_INTERROGATION, fun, inf, entry.getValue(), 0));
            }
            writeVariable(out, linkAddress, Iec103Codec.encodeAsdu(
                    Iec103Types.ASDU_GI_TERMINATION,
                    Iec103Types.COT_GI_TERMINATION,
                    asduAddress,
                    0,
                    0,
                    new byte[] { 0 }
            ));
        }

        private static void writeVariable(OutputStream out, int linkAddress, byte[] asdu) throws IOException {
            out.write(Iec103Codec.encodeVariable(Iec103Types.FC_RESPOND_USER_DATA, linkAddress, asdu));
            out.flush();
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
