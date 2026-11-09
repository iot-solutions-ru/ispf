package com.ispf.driver.stomp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverReconnectPendingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loopback tests for {@link StompDeviceDriver} against an in-process broker. */
class StompDeviceDriverTest {

    private static final String BODY = "[{\"value\":[{\"description\":\"Virtual Device\"}]}]";

    private StompDeviceDriver driver;
    private FakeStompServer tcp;
    private FakeWebSocketStompServer web;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (tcp != null) {
            tcp.close();
            tcp = null;
        }
        if (web != null) {
            web.close();
            web = null;
        }
    }

    @Test
    void tcpSubscribesReadsJsonAndSends() throws Exception {
        tcp = new FakeStompServer(false);
        tcp.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "transport", "tcp",
                "host", "127.0.0.1",
                "port", String.valueOf(tcp.port()),
                "login", "admin",
                "passcode", "secret",
                "connectHeaders", "{\"Authorization\":\"Bearer lab-token\"}",
                "timeoutMs", "2000"
        ));
        driver = new StompDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());
        assertTrue(tcp.connectText().contains("login:admin"));
        assertTrue(tcp.connectText().contains("passcode:secret"));
        assertTrue(tcp.connectText().contains("Authorization:Bearer lab-token"));
        driver.readPoints(Map.of(
                "state", "@state",
                "name", "/topic/info|$.0.value.0.description",
                "raw", "/topic/info|@raw"
        ));
        assertEquals("Open", value(object, "state"));
        assertEquals("Virtual Device", value(object, "name"));
        assertEquals(BODY, value(object, "raw"));
        driver.writePoint("name", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")));
        assertEquals("24.1", tcp.awaitSend());
    }

    @Test
    void rejectedLoginIsNotRetried() throws Exception {
        tcp = new FakeStompServer(true);
        tcp.start();
        long[] now = {1_000L};
        driver = new StompDeviceDriver();
        driver.clock = () -> now[0];
        driver.initialize(new StubDriverObject(Map.of(
                "transport", "tcp",
                "host", "127.0.0.1",
                "port", String.valueOf(tcp.port()),
                "login", "admin",
                "passcode", "wrong",
                "timeoutMs", "2000"
        )));
        DriverException rejected = assertThrows(DriverException.class, driver::connect);
        assertInstanceOf(DriverConfigurationException.class, rejected);
        DriverException again = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("name", "/topic/info")));
        assertInstanceOf(DriverConfigurationException.class, again);
        assertTrue(again.getMessage().contains("not usable"));
    }

    @Test
    void websocketSubscribesAndReads() throws Exception {
        web = new FakeWebSocketStompServer();
        web.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "transport", "websocket",
                "host", "127.0.0.1",
                "port", String.valueOf(web.port()),
                "path", "/rest/v1/stomp",
                "timeoutMs", "2000"
        ));
        driver = new StompDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of("name", "/v1/contexts/devices.virtual/variables/info|$.0.value.0.description"));
        assertEquals("Virtual Device", value(object, "name"));
    }

    @Test
    void authUrlPutsTheRawTokenOnConnect() throws Exception {
        com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/auth", exchange -> {
            byte[] body = "{\"token\":\"lab.jwt.value\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();
        try {
            tcp = new FakeStompServer(false);
            tcp.start();
            driver = new StompDeviceDriver();
            driver.initialize(new StubDriverObject(Map.of(
                    "transport", "tcp",
                    "host", "127.0.0.1",
                    "port", String.valueOf(tcp.port()),
                    "login", "admin",
                    "passcode", "secret",
                    "authUrl", "http://127.0.0.1:" + http.getAddress().getPort() + "/auth",
                    "connectHeaders", "{\"Authorization\":\"Bearer leftover\"}",
                    "timeoutMs", "2000"
            )));
            driver.connect();
            assertTrue(tcp.connectText().contains("Authorization:lab.jwt.value"));
            assertTrue(!tcp.connectText().contains("Bearer leftover"));
        } finally {
            http.stop(0);
        }
    }

    @Test
    void customCaStillChecksTheServerHostname() throws Exception {
        java.nio.file.Path cert = java.nio.file.Files.createTempFile("stomp-ca", ".pem");
        try (InputStream in = StompDeviceDriverTest.class.getResourceAsStream("/stomp-test-ca.pem")) {
            java.nio.file.Files.copy(in, cert, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            javax.net.ssl.HttpsURLConnection https = (javax.net.ssl.HttpsURLConnection)
                    java.net.URI.create("https://broker.example/auth").toURL().openConnection();
            javax.net.ssl.HostnameVerifier before = https.getHostnameVerifier();
            StompDeviceDriver.configureAuthTls(https, true, cert.toString());
            assertSame(before, https.getHostnameVerifier());

            javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket)
                    javax.net.ssl.SSLContext.getDefault().getSocketFactory().createSocket();
            try {
                StompWebSocket.requireHttpsEndpoint(ssl);
                assertEquals("HTTPS", ssl.getSSLParameters().getEndpointIdentificationAlgorithm());
            } finally {
                ssl.close();
            }
        } finally {
            java.nio.file.Files.deleteIfExists(cert);
        }
    }

    @Test
    void retriesConnectAfterTheBackoffWhenThePortReturns() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        long[] now = {1_000L};
        StubDriverObject object = new StubDriverObject(Map.of(
                "transport", "tcp",
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "300"
        ));
        driver = new StompDeviceDriver();
        driver.clock = () -> now[0];
        driver.initialize(object);
        assertThrows(DriverException.class, driver::connect);
        assertInstanceOf(DriverReconnectPendingException.class, assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("name", "/topic/info"))));
        now[0] += 5_000L;
        tcp = new FakeStompServer(false, port);
        tcp.start();
        driver.readPoints(Map.of("name", "/topic/info|$.0.value.0.description"));
        assertEquals("Virtual Device", value(object, "name"));
    }

    private static String value(StubDriverObject object, String name) {
        return String.valueOf(object.variables.get(name).firstRow().get("value"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        private StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-stomp", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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

    /** In-process STOMP broker on TCP. A rejected login answers ERROR; otherwise CONNECTED and one MESSAGE. */
    private static final class FakeStompServer implements AutoCloseable {
        private final boolean rejectLogin;
        private final ServerSocket serverSocket;
        private final AtomicReference<String> connectText = new AtomicReference<>("");
        private final AtomicReference<String> sentBody = new AtomicReference<>();
        private final CountDownLatch sent = new CountDownLatch(1);
        private volatile boolean open = true;
        private Thread thread;

        private FakeStompServer(boolean rejectLogin) throws IOException {
            this(rejectLogin, 0);
        }

        private FakeStompServer(boolean rejectLogin, int port) throws IOException {
            this.rejectLogin = rejectLogin;
            this.serverSocket = new ServerSocket(port);
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void start() {
            thread = new Thread(this::acceptLoop, "fake-stomp");
            thread.setDaemon(true);
            thread.start();
        }

        private String connectText() {
            return connectText.get();
        }

        private String awaitSend() throws InterruptedException {
            assertTrue(sent.await(3, TimeUnit.SECONDS));
            return sentBody.get();
        }

        private void acceptLoop() {
            try (Socket socket = serverSocket.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (open) {
                    String frame = readFrame(in);
                    if (frame == null) {
                        return;
                    }
                    String command = frame.startsWith("CONNECT") ? "CONNECT"
                            : frame.startsWith("SUBSCRIBE") ? "SUBSCRIBE"
                            : frame.startsWith("SEND") ? "SEND"
                            : frame.startsWith("UNSUBSCRIBE") ? "UNSUBSCRIBE" : "";
                    if ("CONNECT".equals(command)) {
                        connectText.set(frame);
                        if (rejectLogin) {
                            out.write(frame("ERROR", "message:Authentication failed", ""));
                        } else {
                            out.write(frame("CONNECTED", "version:1.2", ""));
                        }
                        out.flush();
                    } else if ("SUBSCRIBE".equals(command)) {
                        out.write("\n".getBytes(StandardCharsets.UTF_8));
                        out.write(frame("MESSAGE", "destination:/topic/info\nsubscription:s0\ncontent-type:application/json", BODY));
                        out.flush();
                    } else if ("SEND".equals(command)) {
                        int blank = frame.indexOf("\n\n");
                        sentBody.set(blank < 0 ? "" : frame.substring(blank + 2).replace("\0", ""));
                        sent.countDown();
                    }
                }
            } catch (IOException ignored) {
                // test closed the socket
            }
        }

        @Override
        public void close() throws IOException {
            open = false;
            serverSocket.close();
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    /** In-process WebSocket peer that completes the handshake and then speaks STOMP. */
    private static final class FakeWebSocketStompServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private volatile boolean open = true;
        private Thread thread;

        private FakeWebSocketStompServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void start() {
            thread = new Thread(this::acceptLoop, "fake-stomp-ws");
            thread.setDaemon(true);
            thread.start();
        }

        private void acceptLoop() {
            try (Socket socket = serverSocket.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String key = "";
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                        key = line.substring(line.indexOf(':') + 1).trim();
                    }
                }
                String accept = StompWebSocket.acceptKey(key);
                out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String connect = new String(readClientText(in), StandardCharsets.UTF_8);
                if (!connect.startsWith("CONNECT")) {
                    return;
                }
                writeServerText(out, new String(frame("CONNECTED", "version:1.2", ""), StandardCharsets.UTF_8));
                String subscribe = new String(readClientText(in), StandardCharsets.UTF_8);
                if (!subscribe.startsWith("SUBSCRIBE")) {
                    return;
                }
                writeServerText(out, new String(frame(
                        "MESSAGE",
                        "destination:/v1/contexts/devices.virtual/variables/info\nsubscription:s0",
                        BODY
                ), StandardCharsets.UTF_8));
                out.flush();
                while (open) {
                    if (in.read() < 0) {
                        return;
                    }
                }
            } catch (IOException ignored) {
                // test closed the socket
            }
        }

        @Override
        public void close() throws IOException {
            open = false;
            serverSocket.close();
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private static byte[] frame(String command, String headers, String body) {
        String text = command + "\n" + headers + "\n\n" + body;
        byte[] head = text.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[head.length + 1];
        System.arraycopy(head, 0, frame, 0, head.length);
        frame[head.length] = 0;
        return frame;
    }

    private static String readFrame(InputStream in) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                return frame.size() == 0 ? null : frame.toString(StandardCharsets.UTF_8);
            }
            if (b == 0) {
                return frame.toString(StandardCharsets.UTF_8);
            }
            frame.write(b);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
            }
            if (b == '\n') {
                return line.toString(StandardCharsets.US_ASCII);
            }
            if (b != '\r') {
                line.write(b);
            }
        }
    }

    private static byte[] readClientText(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) {
            throw new IOException("closed");
        }
        int len = b1 & 0x7F;
        byte[] mask = in.readNBytes(4);
        byte[] payload = in.readNBytes(len);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        return payload;
    }

    private static void writeServerText(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        out.write(0x81);
        if (payload.length < 126) {
            out.write(payload.length);
        } else {
            out.write(126);
            out.write((payload.length >>> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        }
        out.write(payload);
        out.flush();
    }
}
