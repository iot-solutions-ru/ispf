package com.ispf.driver.websocket;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
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

/** Loopback tests for {@link WebsocketDeviceDriver} against an in-process fake RFC6455 server. */
class WebsocketDeviceDriverTest {

    private WebsocketDeviceDriver driver;
    private FakeWebSocketServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) { driver.disconnect(); driver = null; }
        if (server != null) { server.close(); server = null; }
    }

    @Test
    void getAndSetViaMessageKeyAndChannelPath() throws Exception {
        server = new FakeWebSocketServer();
        server.put("temperature", "23.5");
        server.put("/sensors/pressure", "101.3");
        server.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "path", "/ws",
                "timeoutMs", "2000"
        ));
        driver = new WebsocketDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of("temp", "temperature", "pressure", "/sensors/pressure"));
        assertEquals("23.5", object.variables.get("temp").firstRow().get("value"));
        assertEquals("temperature", object.variables.get("temp").firstRow().get("point"));
        assertEquals("101.3", object.variables.get("pressure").firstRow().get("value"));
        assertEquals("/sensors/pressure", object.variables.get("pressure").firstRow().get("point"));
        driver.writePoint("temp", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")));
        assertEquals("24.1", server.get("temperature"));
        assertEquals("24.1", object.variables.get("temp").firstRow().get("value"));
        driver.readPoints(Map.of("temp", "temperature"));
        assertEquals("24.1", object.variables.get("temp").firstRow().get("value"));
    }

    @Test
    void getModeRawPointKeepsTheLastReplyText() throws Exception {
        server = new FakeWebSocketServer();
        server.put("temperature", "23.5");
        server.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "path", "/",
                "timeoutMs", "2000"
        ));
        driver = new WebsocketDeviceDriver();
        driver.initialize(object);
        driver.connect();
        java.util.Map<String, String> mappings = new java.util.LinkedHashMap<>();
        mappings.put("temp", "temperature");
        mappings.put("raw", "@raw");
        driver.readPoints(mappings);
        assertEquals("23.5", value(object, "temp"));
        assertEquals("{\"point\":\"temperature\",\"value\":\"23.5\"}", value(object, "raw"));
    }

    @Test
    void acceptKeyMatchesRfc6455Example() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                Rfc6455Client.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void subscribeReadsLatestBallsPublish() throws Exception {
        server = new FakeWebSocketServer();
        server.start();
        String subscribe = "{\"op\":\"subscribe\",\"id\":\"balls\",\"topic\":\"/balls\","
                + "\"type\":\"tf2_msgs/msg/TFMessage\",\"throttle_rate\":200,\"queue_length\":1}";
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(server.port()),
                "path", "/",
                "timeoutMs", "2000",
                "subscribeText", subscribe
        ));
        driver = new WebsocketDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of(
                "state", "@state",
                "op", "$.op",
                "count", "$.msg.transforms.length",
                "name", "$.msg.transforms.0.child_frame_id",
                "x", "round:3:$.msg.transforms.0.transform.translation.x",
                "y", "round:3:$.msg.transforms.0.transform.translation.y",
                "z", "round:3:$.msg.transforms.0.transform.translation.z",
                "raw", "@raw"
        ));
        assertEquals("Open", value(object, "state"));
        assertEquals("publish", value(object, "op"));
        assertEquals("2", value(object, "count"));
        assertEquals("ball_0", value(object, "name"));
        assertEquals("1.235", value(object, "x"));
        assertEquals("-0.5", value(object, "y"));
        assertEquals("0", value(object, "z"));
        assertEquals(BALLS_PUBLISH, value(object, "raw"));
        assertThrows(DriverException.class, () -> driver.writePoint("x", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "1"))));
    }

    private static String value(StubDriverObject object, String name) {
        return String.valueOf(object.variables.get(name).firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new WebsocketDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("k", "temperature")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void tlsGetTrustsTheCaFile() throws Exception {
        Path dir = Files.createTempDirectory("ispf-ws-tls");
        try {
            SelfSignedTls tls = SelfSignedTls.create(dir);
            server = new FakeWebSocketServer(tls.serverContext());
            server.put("temperature", "23.5");
            server.start();
            StubDriverObject object = new StubDriverObject(Map.of(
                    "host", "127.0.0.1",
                    "port", String.valueOf(server.port()),
                    "path", "/ws",
                    "timeoutMs", "2000",
                    "useTls", "true",
                    "caCertPath", tls.caPem().toString()
            ));
            driver = new WebsocketDeviceDriver();
            driver.initialize(object);
            driver.connect();
            assertTrue(driver.isConnected());
            driver.readPoints(Map.of("temp", "temperature"));
            assertEquals("23.5", value(object, "temp"));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    void tlsWithoutTheCaFileFails() throws Exception {
        Path dir = Files.createTempDirectory("ispf-ws-tls");
        try {
            SelfSignedTls tls = SelfSignedTls.create(dir);
            server = new FakeWebSocketServer(tls.serverContext());
            server.start();
            StubDriverObject object = new StubDriverObject(Map.of(
                    "host", "127.0.0.1",
                    "port", String.valueOf(server.port()),
                    "timeoutMs", "2000",
                    "useTls", "true"
            ));
            driver = new WebsocketDeviceDriver();
            driver.initialize(object);
            DriverException error = assertThrows(DriverException.class, driver::connect);
            assertTrue(error.getMessage().contains("WebSocket connect failed"));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    void connectFailsAgainstClosedPort() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1", "port", String.valueOf(closedPort), "timeoutMs", "200"));
        driver = new WebsocketDeviceDriver();
        driver.initialize(object);
        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("WebSocket connect failed"));
    }

    private static final String BALLS_PUBLISH = "{\"op\":\"publish\",\"topic\":\"/balls\",\"msg\":{\"transforms\":["
            + "{\"child_frame_id\":\"ball_0\",\"transform\":{\"translation\":{\"x\":1.2346,\"y\":-0.5,\"z\":0.0}}},"
            + "{\"child_frame_id\":\"ball_1\",\"transform\":{\"translation\":{\"x\":9,\"y\":9,\"z\":9}}}"
            + "]}}";

    private static final class FakeWebSocketServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-ws-server");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> store = new ConcurrentHashMap<>();
        FakeWebSocketServer() throws IOException {
            this(null);
        }
        FakeWebSocketServer(SSLContext tls) throws IOException {
            if (tls == null) {
                serverSocket = new ServerSocket();
            } else {
                serverSocket = tls.getServerSocketFactory().createServerSocket();
            }
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }
        int port() { return serverSocket.getLocalPort(); }
        void put(String key, String value) { store.put(key, value); }
        String get(String key) { return store.get(key); }
        void start() { var _ = executor.submit(this::acceptLoop); }
        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    var _ = executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) { return; }
                }
            }
        }
        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                Rfc6455Client.ServerHelpers.completeHandshake(in, out, new HashMap<>());
                while (true) {
                    String text = Rfc6455Client.ServerHelpers.readMaskedText(in);
                    String op = Rfc6455Client.extractJsonField(text, "op");
                    String point = Rfc6455Client.extractJsonField(text, "point");
                    if ("subscribe".equals(op)) {
                        Rfc6455Client.ServerHelpers.writeUnmaskedText(out,
                                "{\"op\":\"status\",\"level\":\"info\"}");
                        Rfc6455Client.ServerHelpers.writeUnmaskedText(out, BALLS_PUBLISH);
                        continue;
                    }
                    if ("set".equals(op)) {
                        String value = Rfc6455Client.extractJsonField(text, "value");
                        store.put(point == null ? "" : point, value == null ? "" : value);
                        Rfc6455Client.ServerHelpers.writeUnmaskedText(out,
                                "{\"ok\":true,\"point\":\"" + Rfc6455Client.escapeJson(point)
                                        + "\",\"value\":\"" + Rfc6455Client.escapeJson(value) + "\"}");
                    } else {
                        String value = store.getOrDefault(point, "");
                        Rfc6455Client.ServerHelpers.writeUnmaskedText(out,
                                "{\"point\":\"" + Rfc6455Client.escapeJson(point)
                                        + "\",\"value\":\"" + Rfc6455Client.escapeJson(value) + "\"}");
                    }
                }
            } catch (IOException ignored) { }
        }
        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class SelfSignedTls {
        private final SSLContext serverContext;
        private final Path caPem;

        private SelfSignedTls(SSLContext serverContext, Path caPem) {
            this.serverContext = serverContext;
            this.caPem = caPem;
        }

        SSLContext serverContext() { return serverContext; }
        Path caPem() { return caPem; }

        static SelfSignedTls create(Path dir) throws Exception {
            Path keystore = dir.resolve("server.p12");
            Path caPem = dir.resolve("ca.pem");
            String keytoolName = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "keytool.exe" : "keytool";
            Path keytool = Path.of(System.getProperty("java.home"), "bin", keytoolName);
            run(keytool, "-genkeypair",
                    "-alias", "server",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "PKCS12",
                    "-keystore", keystore.toString(),
                    "-storepass", "changeit",
                    "-keypass", "changeit",
                    "-dname", "CN=127.0.0.1",
                    "-ext", "san=ip:127.0.0.1",
                    "-validity", "2");
            run(keytool, "-exportcert",
                    "-alias", "server",
                    "-keystore", keystore.toString(),
                    "-storepass", "changeit",
                    "-rfc",
                    "-file", caPem.toString());
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystore)) {
                store.load(in, "changeit".toCharArray());
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, "changeit".toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            return new SelfSignedTls(context, caPem);
        }

        private static void run(Path keytool, String... args) throws Exception {
            String[] command = new String[args.length + 1];
            command[0] = keytool.toString();
            System.arraycopy(args, 0, command, 1, args.length);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("keytool failed: " + output);
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) { }
            });
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();
        StubDriverObject(Map<String, String> configuration) { this.configuration = configuration; }
        @Override public PlatformObject deviceObject() {
            return new PlatformObject("test-websocket", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
        }
        @Override public void updateVariable(String name, DataRecord value) { variables.put(name, value); }
        @Override public Optional<DataRecord> getVariable(String name) { return Optional.ofNullable(variables.get(name)); }
        @Override public void log(DeviceDriver.DriverLogLevel level, String message) { }
        @Override public Map<String, String> configuration() { return configuration; }
    }
}
