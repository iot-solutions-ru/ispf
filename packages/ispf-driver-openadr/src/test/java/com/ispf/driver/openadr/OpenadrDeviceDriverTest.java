package com.ispf.driver.openadr;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenadrDeviceDriverTest {

    /**
     * Handwritten OpenADR 2.0b oadrPoll body (source of truth — not taken from the encoder).
     * Content-Length for this body is 219.
     */
    private static final String OADR_POLL_BODY =
            "<oadrPayload xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\">"
                    + "<oadrSignedObject><oadrPoll>"
                    + "<venID xmlns=\"http://docs.oasis-open.org/ns/energyinterop/201110\">ven-ispf-1</venID>"
                    + "</oadrPoll></oadrSignedObject></oadrPayload>";

    private static final int OADR_POLL_CONTENT_LENGTH = 219;

    /** Handwritten HTTP/1.1 POST for default host 127.0.0.1 port 8080. */
    private static final String OADR_POLL_REQUEST_DEFAULT =
            "POST /OpenADR2/Simple/2.0b/OadrPoll HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:8080\r\n"
                    + "Content-Type: application/xml\r\n"
                    + "Connection: close\r\n"
                    + "Content-Length: " + OADR_POLL_CONTENT_LENGTH + "\r\n"
                    + "\r\n"
                    + OADR_POLL_BODY;

    /** Small oadrPayload response written by the ServerSocket peer. */
    private static final String OADR_RESPONSE_BODY =
            "<oadrPayload xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\">"
                    + "<oadrSignedObject><oadrDistributeEvent>"
                    + "<oadrEvent><eiEvent xmlns=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                    + "<eventDescriptor><eventID>evt-42</eventID></eventDescriptor>"
                    + "<eiEventSignals><eiEventSignal>"
                    + "<signalName>SIMPLE</signalName>"
                    + "<currentValue>2</currentValue>"
                    + "</eiEventSignal></eiEventSignals>"
                    + "</eiEvent></oadrEvent>"
                    + "</oadrDistributeEvent></oadrSignedObject></oadrPayload>";

    private OpenadrDeviceDriver driver;
    private FakeVtn peer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (peer != null) {
            peer.close();
            peer = null;
        }
    }

    @Test
    void oadrPollBodyAndDefaultRequestMatchHandwrittenLiterals() {
        assertEquals(OADR_POLL_CONTENT_LENGTH, OADR_POLL_BODY.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(OADR_POLL_BODY, OpenadrDeviceDriver.buildOadrPollBody("ven-ispf-1"));
        assertEquals(OADR_POLL_REQUEST_DEFAULT,
                OpenadrDeviceDriver.buildOadrPollRequest(
                        "127.0.0.1", 8080, OpenadrDeviceDriver.DEFAULT_POLL_PATH, OADR_POLL_BODY));
    }

    @Test
    void metadataDescribesOpenAdrOadrPollWithoutLab() {
        driver = new OpenadrDeviceDriver();
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("openadr 2.0b oadrpoll")
                || (description.contains("openadr 2.0b") && description.contains("oadrpoll")));
        assertFalse(description.contains("lab"));
    }

    @Test
    void pollsOadrPollOverHttpAndReadsSignal() throws Exception {
        peer = new FakeVtn();
        peer.start();
        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(peer.port()),
                "pollPath", OpenadrDeviceDriver.DEFAULT_POLL_PATH,
                "venId", "ven-ispf-1",
                "timeoutMs", "3000"
        ));
        driver = new OpenadrDeviceDriver();
        driver.initialize(object);
        driver.connect();
        driver.readPoints(Map.of(
                "id", "eventId",
                "level", "signalLevel",
                "name", "signalName",
                "on", "active"
        ));

        String expectedRequest =
                "POST /OpenADR2/Simple/2.0b/OadrPoll HTTP/1.1\r\n"
                        + "Host: 127.0.0.1:" + peer.port() + "\r\n"
                        + "Content-Type: application/xml\r\n"
                        + "Connection: close\r\n"
                        + "Content-Length: " + OADR_POLL_CONTENT_LENGTH + "\r\n"
                        + "\r\n"
                        + OADR_POLL_BODY;
        assertEquals(expectedRequest, peer.lastRawRequest());
        assertEquals(OADR_POLL_BODY, peer.lastBody());
        assertEquals("evt-42", object.variables.get("id").firstRow().get("value"));
        assertEquals("2", object.variables.get("level").firstRow().get("value"));
        assertEquals("SIMPLE", object.variables.get("name").firstRow().get("value"));
        assertEquals("true", object.variables.get("on").firstRow().get("value"));
    }

    @Test
    void readBeforeConnectThrows() {
        OpenadrDeviceDriver local = new OpenadrDeviceDriver();
        local.initialize(new StubDriverObject(Map.of()));
        DriverException error = assertThrows(DriverException.class, () ->
                local.readPoints(Map.of("id", "eventId")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    private static final class FakeVtn implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-openadr-vtn");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<String> lastRawRequest = new AtomicReference<>("");
        private final AtomicReference<String> lastBody = new AtomicReference<>("");

        FakeVtn() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String lastRawRequest() {
            return lastRawRequest.get();
        }

        String lastBody() {
            return lastBody.get();
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
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                String raw = readRequest(in);
                lastRawRequest.set(raw);
                int headerEnd = raw.indexOf("\r\n\r\n");
                lastBody.set(headerEnd >= 0 ? raw.substring(headerEnd + 4) : "");
                byte[] bodyBytes = OADR_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);
                String response = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/xml\r\n"
                        + "Content-Length: " + bodyBytes.length + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + OADR_RESPONSE_BODY;
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
            }
        }

        private static String readRequest(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[512];
            while (true) {
                int n = in.read(tmp);
                if (n < 0) {
                    break;
                }
                buf.write(tmp, 0, n);
                String soFar = buf.toString(StandardCharsets.US_ASCII);
                int headerEnd = soFar.indexOf("\r\n\r\n");
                if (headerEnd >= 0) {
                    int contentLength = 0;
                    for (String line : soFar.substring(0, headerEnd).split("\r\n")) {
                        if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        }
                    }
                    int bodyStart = headerEnd + 4;
                    while (buf.size() < bodyStart + contentLength) {
                        n = in.read(tmp);
                        if (n < 0) {
                            break;
                        }
                        buf.write(tmp, 0, n);
                    }
                    break;
                }
            }
            return buf.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            executor.shutdownNow();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> configuration;
        final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-openadr", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
