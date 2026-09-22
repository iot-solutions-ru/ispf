package com.ispf.driver.lsxgt;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link LsXgtDeviceDriver} against an in-process XGT dedicated TCP peer.
 */
class LsXgtDeviceDriverTest {

    private LsXgtDeviceDriver driver;
    private FakeXgtPeer xgtServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (xgtServer != null) {
            xgtServer.close();
            xgtServer = null;
        }
    }

    @Test
    void dw0ReadIsLiteralDedicatedFrame() {
        // Handwritten XGT dedicated read of %DW0, invoke id 1 (header then instruction).
        byte[] expected = new byte[] {
                0x4C, 0x53, 0x49, 0x53, 0x2D, 0x58, 0x47, 0x54, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x33, 0x01, 0x00, 0x0E, 0x00, 0x00, (byte) 0x9D,
                0x54, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01, 0x00, 0x04, 0x00,
                0x25, 0x44, 0x57, 0x30
        };
        byte[] actual = LsXgtDeviceDriver.encodeRead(LsXgtPoint.parse("%DW0"), 1);
        assertArrayEquals(expected, actual);
    }

    @Test
    void readsAndWritesDeviceMemoryViaLoopback() throws Exception {
        xgtServer = new FakeXgtPeer();
        xgtServer.put(LsXgtPoint.DeviceType.DW, 100, 0x1234);
        xgtServer.put(LsXgtPoint.DeviceType.DW, 101, 0x00AB);
        xgtServer.put(LsXgtPoint.DeviceType.MW, 10, 7);
        xgtServer.put(LsXgtPoint.DeviceType.MX, 0, 1);
        xgtServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(xgtServer.port())
        ));
        driver = new LsXgtDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertEquals("ls-xgt", driver.metadata().id());
        assertTrue(driver.metadata().description().toLowerCase(Locale.ROOT).contains("dedicated"));
        assertTrue(!driver.metadata().description().toLowerCase(Locale.ROOT).contains("lab"));

        driver.readPoints(Map.of(
                "pair", "%DW100:2",
                "mw", "MW10",
                "bit", "%MX0"
        ));

        assertEquals("4660,171", object.variables.get("pair").firstRow().get("value"));
        assertEquals("DW", object.variables.get("pair").firstRow().get("device"));
        assertEquals("7", object.variables.get("mw").firstRow().get("value"));
        assertEquals("1", object.variables.get("bit").firstRow().get("value"));

        driver.writePoint("mw", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.INTEGER).build(),
                Map.of("value", 99)
        ));
        assertEquals(99, xgtServer.get(LsXgtPoint.DeviceType.MW, 10));
        assertEquals("99", object.variables.get("mw").firstRow().get("value"));
    }

    @Test
    void pointParserAcceptsFormats() {
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.DW, 100, 1),
                LsXgtPoint.parse("%DW100"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.DW, 100, 1),
                LsXgtPoint.parse("DW100"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.DW, 100, 2),
                LsXgtPoint.parse("%DW100:2"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.MW, 10, 1),
                LsXgtPoint.parse("%MW10"));
        assertEquals(
                new LsXgtPoint(LsXgtPoint.DeviceType.MX, 0, 1),
                LsXgtPoint.parse("%MX0"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new LsXgtDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("a", "%DW1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeXgtPeer implements AutoCloseable {

        private static final Pattern VAR = Pattern.compile("^%([DM][WX])(\\d+)$");

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-xgt-dedicated-peer");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Integer> memory = new ConcurrentHashMap<>();

        FakeXgtPeer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(LsXgtPoint.DeviceType type, int address, int value) {
            memory.put(key(type, address), value & 0xFFFF);
        }

        int get(LsXgtPoint.DeviceType type, int address) {
            return memory.getOrDefault(key(type, address), 0);
        }

        private static String key(LsXgtPoint.DeviceType type, int address) {
            return type.name() + ":" + address;
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
                    byte[] header = new byte[LsXgtDeviceDriver.HEADER_LEN];
                    in.readFully(header);
                    if (!Arrays.equals(
                            Arrays.copyOf(header, LsXgtDeviceDriver.COMPANY_ID.length),
                            LsXgtDeviceDriver.COMPANY_ID)) {
                        return;
                    }
                    int instrLen = (header[16] & 0xFF) | ((header[17] & 0xFF) << 8);
                    int invoke = (header[14] & 0xFF) | ((header[15] & 0xFF) << 8);
                    byte[] instruction = new byte[instrLen];
                    in.readFully(instruction);
                    ByteBuffer body = ByteBuffer.wrap(instruction).order(ByteOrder.LITTLE_ENDIAN);
                    int command = body.getShort() & 0xFFFF;
                    int dataType = body.getShort() & 0xFFFF;
                    body.getShort(); // reserved
                    int blocks = body.getShort() & 0xFFFF;

                    if (command == LsXgtDeviceDriver.CMD_WRITE) {
                        String varName = readVarName(body);
                        int dataSize = body.getShort() & 0xFFFF;
                        int value;
                        if (dataType == LsXgtDeviceDriver.DATA_TYPE_BIT) {
                            value = body.get() & 0x01;
                            for (int s = 1; s < dataSize; s++) {
                                body.get();
                            }
                        } else {
                            value = body.getShort() & 0xFFFF;
                            for (int s = 2; s < dataSize; s++) {
                                body.get();
                            }
                        }
                        applyVar(varName, value);
                        out.write(responseHeader(invoke, 6));
                        ByteBuffer resp = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
                        resp.putShort((short) LsXgtDeviceDriver.CMD_WRITE);
                        resp.putShort((short) dataType);
                        resp.putShort((short) 0); // error
                        out.write(resp.array());
                        out.flush();
                    } else if (command == LsXgtDeviceDriver.CMD_READ) {
                        String[] names = new String[blocks];
                        for (int i = 0; i < blocks; i++) {
                            names[i] = readVarName(body);
                        }
                        int payload = 8; // cmd+type+error+blocks
                        for (int i = 0; i < blocks; i++) {
                            payload += 2 + (dataType == LsXgtDeviceDriver.DATA_TYPE_BIT ? 1 : 2);
                        }
                        out.write(responseHeader(invoke, payload));
                        ByteBuffer resp = ByteBuffer.allocate(payload).order(ByteOrder.LITTLE_ENDIAN);
                        resp.putShort((short) LsXgtDeviceDriver.CMD_READ);
                        resp.putShort((short) dataType);
                        resp.putShort((short) 0);
                        resp.putShort((short) blocks);
                        for (String name : names) {
                            int value = lookupVar(name);
                            if (dataType == LsXgtDeviceDriver.DATA_TYPE_BIT) {
                                resp.putShort((short) 1);
                                resp.put((byte) (value & 0x01));
                            } else {
                                resp.putShort((short) 2);
                                resp.putShort((short) (value & 0xFFFF));
                            }
                        }
                        out.write(resp.array());
                        out.flush();
                    }
                }
            } catch (EOFException ignored) {
            } catch (IOException | RuntimeException ignored) {
            }
        }

        private static String readVarName(ByteBuffer body) {
            int len = body.getShort() & 0xFFFF;
            byte[] raw = new byte[len];
            body.get(raw);
            return new String(raw, StandardCharsets.US_ASCII);
        }

        private void applyVar(String varName, int value) {
            Matcher matcher = VAR.matcher(varName.toUpperCase(Locale.ROOT));
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Bad var " + varName);
            }
            LsXgtPoint.DeviceType type = LsXgtPoint.DeviceType.valueOf(matcher.group(1));
            put(type, Integer.parseInt(matcher.group(2)), value);
        }

        private int lookupVar(String varName) {
            Matcher matcher = VAR.matcher(varName.toUpperCase(Locale.ROOT));
            if (!matcher.matches()) {
                return 0;
            }
            LsXgtPoint.DeviceType type = LsXgtPoint.DeviceType.valueOf(matcher.group(1));
            return get(type, Integer.parseInt(matcher.group(2)));
        }

        private static byte[] responseHeader(int invoke, int instrLen) {
            ByteBuffer buf = ByteBuffer.allocate(LsXgtDeviceDriver.HEADER_LEN).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(LsXgtDeviceDriver.COMPANY_ID);
            buf.putShort((short) 0);
            buf.put((byte) 0);
            buf.put(LsXgtDeviceDriver.SOF_RESPONSE);
            buf.putShort((short) (invoke & 0xFFFF));
            buf.putShort((short) instrLen);
            buf.put((byte) 0);
            int bcc = 0;
            byte[] arr = buf.array();
            for (int i = 0; i < 19; i++) {
                bcc = (bcc + (arr[i] & 0xFF)) & 0xFF;
            }
            buf.put((byte) bcc);
            return buf.array();
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
                    "test-ls-xgt",
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
