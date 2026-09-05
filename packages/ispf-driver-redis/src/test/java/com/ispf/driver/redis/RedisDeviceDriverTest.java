package com.ispf.driver.redis;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * Loopback tests for {@link RedisDeviceDriver} against an in-process fake RESP server.
 */
class RedisDeviceDriverTest {

    private RedisDeviceDriver driver;
    private FakeRedisServer redisServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (redisServer != null) {
            redisServer.close();
            redisServer = null;
        }
    }

    @Test
    void getAndSetViaLoopback() throws Exception {
        redisServer = new FakeRedisServer();
        redisServer.put("sensor:temp", "23.5");
        redisServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(redisServer.port()),
                "timeoutMs", "2000"
        ));
        driver = new RedisDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temperature", "sensor:temp"));
        DataRecord temperature = object.variables.get("temperature");
        assertEquals("23.5", temperature.firstRow().get("value"));
        assertEquals("sensor:temp", temperature.firstRow().get("key"));

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")
        ));
        assertEquals("24.1", redisServer.get("sensor:temp"));
        assertEquals("24.1", object.variables.get("temperature").firstRow().get("value"));

        driver.readPoints(Map.of("temperature", "sensor:temp"));
        assertEquals("24.1", object.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void missingKeyReturnsEmptyString() throws Exception {
        redisServer = new FakeRedisServer();
        redisServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(redisServer.port())
        ));
        driver = new RedisDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("missing", "no-such-key"));
        assertEquals("", object.variables.get("missing").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new RedisDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("k", "key")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void readFailsAgainstUnreachableHost() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200"
        ));
        driver = new RedisDeviceDriver();
        driver.initialize(object);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("k", "key")));
        assertTrue(error.getMessage().contains("Redis GET failed"));
    }

    @Test
    void encodeProducesArrayCommand() {
        byte[] encoded = RedisDeviceDriver.encode("GET", "foo");
        assertEquals("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n", new String(encoded, StandardCharsets.UTF_8));
    }

    private static final class FakeRedisServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-redis-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> store = new ConcurrentHashMap<>();

        FakeRedisServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String key, String value) {
            store.put(key, value);
        }

        String get(String key) {
            return store.get(key);
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
                while (true) {
                    List<String> command = readCommand(in);
                    if (command.isEmpty()) {
                        return;
                    }
                    String name = command.get(0).toUpperCase();
                    switch (name) {
                        case "GET" -> {
                            String key = command.size() > 1 ? command.get(1) : "";
                            String value = store.get(key);
                            out.write(bulk(value));
                        }
                        case "SET" -> {
                            String key = command.size() > 1 ? command.get(1) : "";
                            String value = command.size() > 2 ? command.get(2) : "";
                            store.put(key, value);
                            out.write("+OK\r\n".getBytes(StandardCharsets.US_ASCII));
                        }
                        default -> out.write(("-ERR unknown command '" + name + "'\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                    }
                    out.flush();
                }
            } catch (EOFException ignored) {
                // client closed
            } catch (IOException ignored) {
                // reset
            }
        }

        private static List<String> readCommand(InputStream in) throws IOException {
            int prefix = in.read();
            if (prefix < 0) {
                return List.of();
            }
            if ((char) prefix != '*') {
                throw new IOException("Expected array");
            }
            int argc = Integer.parseInt(readLine(in));
            List<String> parts = new ArrayList<>(argc);
            for (int i = 0; i < argc; i++) {
                int dollar = in.read();
                if (dollar < 0) {
                    throw new EOFException();
                }
                int len = Integer.parseInt(readLine(in));
                byte[] data = in.readNBytes(len);
                in.read(); // \r
                in.read(); // \n
                parts.add(new String(data, StandardCharsets.UTF_8));
            }
            return parts;
        }

        private static byte[] bulk(String value) {
            if (value == null) {
                return "$-1\r\n".getBytes(StandardCharsets.US_ASCII);
            }
            byte[] raw = value.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.writeBytes(("$" + raw.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            buf.writeBytes(raw);
            buf.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
            return buf.toByteArray();
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true) {
                int ch = in.read();
                if (ch < 0) {
                    throw new EOFException();
                }
                if (ch == '\n') {
                    break;
                }
                if (ch != '\r') {
                    line.write(ch);
                }
            }
            return line.toString(StandardCharsets.UTF_8);
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
                    "test-redis",
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
