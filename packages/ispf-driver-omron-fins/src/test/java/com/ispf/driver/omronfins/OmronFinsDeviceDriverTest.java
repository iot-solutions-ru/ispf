package com.ispf.driver.omronfins;

import com.ispf.core.model.DataRecord;
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
import java.util.HashMap;
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
 * Loopback tests for {@link OmronFinsDeviceDriver} against a fake FINS/TCP server bound to an
 * ephemeral port. The fake mirrors the frame layout the driver produces: a 20-byte node-address
 * handshake answered with 24 bytes, then FINS/TCP framed memory-area read commands (0x0101)
 * answered with a FINS response frame carrying known word data.
 */
class OmronFinsDeviceDriverTest {

    private OmronFinsDeviceDriver driver;
    private FakeFinsServer finsServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (finsServer != null) {
            finsServer.close();
            finsServer = null;
        }
    }

    @Test
    void readsMemoryWordsViaLoopback() throws Exception {
        finsServer = new FakeFinsServer()
                .withWords(0x82, 100, 0x1234, 0x00AB)
                .withWords(0x30, 0, 0x0007);
        finsServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(finsServer.port()),
                "destNode", "32",
                "srcNode", "1"
        ));
        driver = new OmronFinsDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "analog", "DM:100:2",
                "flag", "CIO:0:1"
        ));

        DataRecord analog = object.variables.get("analog");
        assertEquals("4660,171", analog.firstRow().get("value"));
        assertEquals("DM", analog.firstRow().get("memoryArea"));
        assertEquals(100, ((Number) analog.firstRow().get("address")).intValue());
        assertEquals(2, ((Number) analog.firstRow().get("count")).intValue());

        DataRecord flag = object.variables.get("flag");
        assertEquals("7", flag.firstRow().get("value"));
        assertEquals("CIO", flag.firstRow().get("memoryArea"));
        assertEquals(0, ((Number) flag.firstRow().get("address")).intValue());
        assertEquals(1, ((Number) flag.firstRow().get("count")).intValue());

        // The driver opens a fresh socket per point per poll.
        assertEquals(2, finsServer.awaitConnectionsHandled(2));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new OmronFinsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("analog", "DM:100:1")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writeIsReadOnly() {
        driver = new OmronFinsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("analog", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void readFailsAgainstUnreachableHost() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort)
        ));
        driver = new OmronFinsDeviceDriver();
        driver.initialize(object);
        // connect() is lazy: it performs no network I/O and always succeeds.
        driver.connect();
        assertTrue(driver.isConnected());

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("analog", "DM:100:1")));
        assertTrue(error.getMessage().contains("Omron FINS read failed"));
    }

    private static final class FakeFinsServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-fins-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Long, int[]> wordsByAreaAndAddress = new HashMap<>();
        private volatile int connectionsHandled;

        FakeFinsServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        FakeFinsServer withWords(int areaCode, int address, int... words) {
            wordsByAreaAndAddress.put(key(areaCode, address), words);
            return this;
        }

        void start() {
            executor.submit(this::serve);
        }

        int awaitConnectionsHandled(int expected) throws InterruptedException {
            for (int attempt = 0; attempt < 20 && connectionsHandled < expected; attempt++) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
            return connectionsHandled;
        }

        private static long key(int areaCode, int address) {
            return ((long) areaCode << 32) | (address & 0xFFFFFFFFL);
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(10_000);
                    handle(socket);
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        return;
                    }
                    // keep accepting further driver connections
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();

                byte[] handshake = new byte[20];
                in.readFully(handshake);

                // FINS/TCP node-address-data-send response (24 bytes); the driver only counts bytes.
                ByteBuffer handshakeResponse = ByteBuffer.allocate(24);
                handshakeResponse.put(new byte[] { 'F', 'I', 'N', 'S' });
                handshakeResponse.putInt(16);
                handshakeResponse.putInt(1);
                handshakeResponse.putInt(0);
                handshakeResponse.putInt(1);
                handshakeResponse.putInt(32);
                out.write(handshakeResponse.array());
                out.flush();

                while (true) {
                    byte[] header = new byte[8];
                    in.readFully(header);
                    int length = ByteBuffer.wrap(header, 4, 4).getInt();
                    byte[] command = new byte[length];
                    in.readFully(command);
                    out.write(buildReadResponse(command));
                    out.flush();
                }
            } catch (EOFException ignored) {
                // driver closed its per-point socket
            } catch (IOException ignored) {
                // malformed frame or reset — back to the accept loop
            } finally {
                connectionsHandled++;
            }
        }

        private byte[] buildReadResponse(byte[] command) {
            int areaCode = command[12] & 0xFF;
            int address = ((command[13] & 0xFF) << 8) | (command[14] & 0xFF);
            int count = ((command[16] & 0xFF) << 8) | (command[17] & 0xFF);
            int[] data = wordsByAreaAndAddress.getOrDefault(key(areaCode, address), new int[0]);

            ByteBuffer payload = ByteBuffer.allocate(14 + count * 2);
            payload.put((byte) 0xC0);   // ICF: response frame
            payload.put((byte) 0x00);   // RSV
            payload.put((byte) 0x02);   // GCT
            payload.put(command[6]);    // DNA <- request SNA
            payload.put(command[7]);    // DA1 <- request SA1
            payload.put(command[8]);    // DA2 <- request SA2
            payload.put(command[3]);    // SNA <- request DNA
            payload.put(command[4]);    // SA1 <- request DA1
            payload.put(command[5]);    // SA2 <- request DA2
            payload.put(command[9]);    // SID echo
            payload.put((byte) 0x01);   // MRC (memory area read)
            payload.put((byte) 0x01);   // SRC
            payload.put((byte) 0x00);   // MRES: normal completion
            payload.put((byte) 0x00);   // SRES
            for (int i = 0; i < count; i++) {
                payload.putShort((short) (i < data.length ? data[i] : 0));
            }

            byte[] body = payload.array();
            ByteBuffer frame = ByteBuffer.allocate(8 + body.length);
            frame.put(new byte[] { 'F', 'I', 'N', 'S' });
            frame.putInt(body.length);
            frame.put(body);
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
                    "test-omron-fins",
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
            // no-op
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
