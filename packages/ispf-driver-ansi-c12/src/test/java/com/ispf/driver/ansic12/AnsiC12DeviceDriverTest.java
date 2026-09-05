package com.ispf.driver.ansic12;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.ansic12.codec.AnsiC12LabCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
 * Fake TCP meter loopback tests for the ANSI C12 lab codec.
 */
class AnsiC12DeviceDriverTest {

    private AnsiC12DeviceDriver driver;
    private FakeAnsiC12MeterServer meter;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (meter != null) {
            meter.close();
            meter = null;
        }
    }

    @Test
    void metadataDescribesLabNotStub() {
        driver = new AnsiC12DeviceDriver();
        assertEquals("ansi-c12", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
        assertTrue(driver.metadata().description().toLowerCase().contains("lab"));
        assertTrue(driver.metadata().description().toLowerCase().contains("not a certified"));
    }

    @Test
    void pointParserAcceptsTableForms() {
        assertEquals(1, AnsiC12Point.parse("1").tableId());
        assertEquals(1, AnsiC12Point.parse("table:1").tableId());
        assertEquals(1, AnsiC12Point.parse("ST1").tableId());
        assertEquals(1, AnsiC12Point.parse("ST-1").tableId());
        assertEquals(23, AnsiC12Point.parse("table:23").tableId());
    }

    @Test
    void logonAndReadTable1Identification() throws Exception {
        meter = new FakeAnsiC12MeterServer();
        meter.putTable(1, "ISPF-LAB-METER-001".getBytes(StandardCharsets.US_ASCII));
        meter.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(meter.port()),
                "timeoutMs", "2000",
                "user", "ISPF",
                "password", "lab"
        ));
        driver = new AnsiC12DeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "ident", "table:1",
                "also", "ST1"
        ));

        DataRecord ident = object.variables.get("ident");
        assertEquals("ISPF-LAB-METER-001", ident.firstRow().get("value"));
        assertEquals(1L, ((Number) ident.firstRow().get("tableId")).longValue());
        assertEquals("ST1", ident.firstRow().get("label"));
        assertEquals("ISPF-LAB-METER-001", object.variables.get("also").firstRow().get("value"));
    }

    @Test
    void writeTableUpdatesFakeMeter() throws Exception {
        meter = new FakeAnsiC12MeterServer();
        meter.putTable(1, "OLD".getBytes(StandardCharsets.US_ASCII));
        meter.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(meter.port()),
                "timeoutMs", "2000"
        ));
        driver = new AnsiC12DeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("ident", "1"));

        DataRecord write = DataRecord.single(
                DataSchema.builder("t").field("value", FieldType.STRING).build(),
                Map.of("value", "NEW-ID")
        );
        driver.writePoint("ident", write);

        assertEquals("NEW-ID", object.variables.get("ident").firstRow().get("value"));
        assertEquals("NEW-ID", new String(meter.tables().get(1), StandardCharsets.US_ASCII));
    }

    @Test
    void readBeforeConnectThrows() {
        driver = new AnsiC12DeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class,
                () -> driver.readPoints(Map.of("ident", "table:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    /**
     * Minimal ANSI C12-lab meter: logon ack + table store.
     */
    private static final class FakeAnsiC12MeterServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ansi-c12-lab");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Integer, byte[]> tables = new ConcurrentHashMap<>();
        private volatile boolean loggedOn;

        FakeAnsiC12MeterServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void putTable(int tableId, byte[] data) {
            tables.put(tableId, data.clone());
        }

        Map<Integer, byte[]> tables() {
            return tables;
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
            loggedOn = false;
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (!socket.isClosed()) {
                    AnsiC12LabCodec.ParsedFrame request = readFrame(in);
                    if (request.isResponse()) {
                        continue;
                    }
                    switch (request.service()) {
                        case AnsiC12LabCodec.SVC_LOGON -> {
                            loggedOn = true;
                            out.write(AnsiC12LabCodec.encodeResponse(
                                    AnsiC12LabCodec.SVC_LOGON, AnsiC12LabCodec.ACK_OK, new byte[0]));
                            out.flush();
                        }
                        case AnsiC12LabCodec.SVC_READ_TABLE -> {
                            if (!loggedOn) {
                                out.write(AnsiC12LabCodec.encodeResponse(
                                        AnsiC12LabCodec.SVC_READ_TABLE, AnsiC12LabCodec.ACK_ERR, new byte[0]));
                                out.flush();
                                break;
                            }
                            int tableId = AnsiC12LabCodec.tableIdFromPayload(request.payload());
                            byte[] data = tables.getOrDefault(tableId, new byte[0]);
                            out.write(AnsiC12LabCodec.encodeResponse(
                                    AnsiC12LabCodec.SVC_READ_TABLE, AnsiC12LabCodec.ACK_OK, data));
                            out.flush();
                        }
                        case AnsiC12LabCodec.SVC_WRITE_TABLE -> {
                            if (!loggedOn || request.payload().length < 2) {
                                out.write(AnsiC12LabCodec.encodeResponse(
                                        AnsiC12LabCodec.SVC_WRITE_TABLE, AnsiC12LabCodec.ACK_ERR, new byte[0]));
                                out.flush();
                                break;
                            }
                            int tableId = AnsiC12LabCodec.tableIdFromPayload(request.payload());
                            byte[] data = new byte[request.payload().length - 2];
                            System.arraycopy(request.payload(), 2, data, 0, data.length);
                            tables.put(tableId, data);
                            out.write(AnsiC12LabCodec.encodeResponse(
                                    AnsiC12LabCodec.SVC_WRITE_TABLE, AnsiC12LabCodec.ACK_OK, new byte[0]));
                            out.flush();
                        }
                        default -> {
                            out.write(AnsiC12LabCodec.encodeResponse(
                                    request.service(), AnsiC12LabCodec.ACK_ERR, new byte[0]));
                            out.flush();
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private static AnsiC12LabCodec.ParsedFrame readFrame(InputStream in) throws IOException {
            int stp = in.read();
            if (stp < 0) {
                throw new EOFException();
            }
            byte identity = (byte) readByte(in);
            byte ctrl = (byte) readByte(in);
            int length = (readByte(in) << 8) | readByte(in);
            byte[] serviceAndPayload = in.readNBytes(length);
            if (serviceAndPayload.length != length) {
                throw new EOFException();
            }
            int crcLo = readByte(in);
            int crcHi = readByte(in);
            byte[] frame = new byte[5 + length + 2];
            frame[0] = AnsiC12LabCodec.STP;
            frame[1] = identity;
            frame[2] = ctrl;
            frame[3] = (byte) ((length >>> 8) & 0xFF);
            frame[4] = (byte) (length & 0xFF);
            System.arraycopy(serviceAndPayload, 0, frame, 5, length);
            frame[5 + length] = (byte) crcLo;
            frame[6 + length] = (byte) crcHi;
            return AnsiC12LabCodec.parse(frame);
        }

        private static int readByte(InputStream in) throws IOException {
            int value = in.read();
            if (value < 0) {
                throw new EOFException();
            }
            return value;
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
                    "test-ansi-c12",
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
