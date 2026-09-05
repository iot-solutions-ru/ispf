package com.ispf.driver.nats;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link NatsDeviceDriver} against an in-process fake NATS server.
 */
class NatsDeviceDriverTest {

    private NatsDeviceDriver driver;
    private FakeNatsServer natsServer;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (natsServer != null) {
            natsServer.close();
            natsServer = null;
        }
    }

    @Test
    void metadataIsProductionReadWrite() {
        driver = new NatsDeviceDriver();
        assertEquals("nats", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read", "write"), driver.metadata().capabilities());
    }

    @Test
    void subscribePublishLoopback() throws Exception {
        natsServer = new FakeNatsServer();
        natsServer.put("sensors.temp", "19.2");
        natsServer.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(natsServer.port()),
                "timeoutMs", "2000",
                "clientName", "test-nats"
        ));
        driver = new NatsDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temperature", "sensors.temp"));
        DataRecord temperature = object.variables.get("temperature");
        assertEquals("19.2", temperature.firstRow().get("value"));
        assertEquals("sensors.temp", temperature.firstRow().get("subject"));

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "20.1")
        ));
        assertEquals("20.1", awaitServerValue(natsServer, "sensors.temp", "20.1", 2000));
        assertEquals("20.1", object.variables.get("temperature").firstRow().get("value"));

        // retained value still available on re-read
        driver.readPoints(Map.of("temperature", "sensors.temp"));
        assertEquals("20.1", object.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new NatsDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("s", "subject")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void connectFailsAgainstClosedPort() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort),
                "timeoutMs", "200"
        ));
        driver = new NatsDeviceDriver();
        driver.initialize(object);

        DriverException error = assertThrows(DriverException.class, driver::connect);
        assertTrue(error.getMessage().contains("NATS connect failed"));
    }


    private static String awaitServerValue(FakeNatsServer server, String subject, String expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            String value = server.get(subject);
            if (expected.equals(value)) {
                return value;
            }
            Thread.sleep(10);
        }
        return server.get(subject);
    }

    private static final class FakeNatsServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-nats");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> store = new ConcurrentHashMap<>();
        private final Map<String, CopyOnWriteArrayList<Subscription>> subs = new ConcurrentHashMap<>();
        private volatile boolean running;

        FakeNatsServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String subject, String value) {
            store.put(subject, value);
        }

        String get(String subject) {
            return store.get(subject);
        }

        void start() {
            running = true;
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException e) {
                    if (!running || serverSocket.isClosed()) {
                        return;
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                out.write(("INFO {\"server_id\":\"fake\",\"version\":\"2.0.0\",\"proto\":1}\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();

                while (running) {
                    String line = NatsDeviceDriver.readLine(in);
                    if (line == null) {
                        return;
                    }
                    if (line.startsWith("CONNECT ")) {
                        // silent OK (verbose=false)
                    } else if (line.startsWith("SUB ")) {
                        String[] parts = line.split(" ");
                        if (parts.length >= 3) {
                            String subject = parts[1];
                            String sid = parts[2];
                            subs.computeIfAbsent(subject, s -> new CopyOnWriteArrayList<>())
                                    .add(new Subscription(sid, out));
                            String retained = store.get(subject);
                            if (retained != null) {
                                deliver(out, subject, sid, retained);
                            }
                        }
                    } else if (line.startsWith("PUB ")) {
                        String[] parts = line.split(" ");
                        if (parts.length >= 3) {
                            String subject = parts[1];
                            int size = Integer.parseInt(parts[parts.length - 1]);
                            byte[] payload = in.readNBytes(size);
                            in.read();
                            in.read();
                            String text = new String(payload, StandardCharsets.UTF_8);
                            store.put(subject, text);
                            CopyOnWriteArrayList<Subscription> list = subs.get(subject);
                            if (list != null) {
                                for (Subscription sub : list) {
                                    deliver(sub.out(), subject, sub.sid(), text);
                                }
                            }
                        }
                    } else if (line.equals("PING")) {
                        out.write("PONG\r\n".getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } else if (line.startsWith("UNSUB ")) {
                        // ignore
                    }
                }
            } catch (EOFException ignored) {
                // client closed
            } catch (IOException ignored) {
                // reset
            }
        }

        private static void deliver(OutputStream out, String subject, String sid, String payload)
                throws IOException {
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.writeBytes(("MSG " + subject + " " + sid + " " + data.length + "\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            buf.writeBytes(data);
            buf.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
            synchronized (out) {
                out.write(buf.toByteArray());
                out.flush();
            }
        }

        @Override
        public void close() throws Exception {
            running = false;
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }

        private record Subscription(String sid, OutputStream out) {
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
                    "test-nats",
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
