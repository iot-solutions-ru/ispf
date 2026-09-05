package com.ispf.driver.pulsar;

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
 * Loopback tests for {@link PulsarDeviceDriver} against an in-process lab TCP broker.
 */
class PulsarDeviceDriverTest {

    private PulsarDeviceDriver driver;
    private FakePulsarBroker broker;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (broker != null) {
            broker.close();
            broker = null;
        }
    }

    @Test
    void publishesAndGetsTopicPayloads() throws Exception {
        broker = new FakePulsarBroker();
        broker.put("sensors/temp", "23.5");
        broker.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port()),
                "timeoutMs", "2000"
        ));
        driver = new PulsarDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("temperature", "sensors/temp"));
        DataRecord temperature = object.variables.get("temperature");
        assertEquals("23.5", temperature.firstRow().get("value"));
        assertEquals("sensors/temp", temperature.firstRow().get("topic"));

        driver.writePoint("temperature", DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.STRING).build(),
                Map.of("value", "24.1")
        ));
        assertEquals("24.1", broker.get("sensors/temp"));
        assertEquals("24.1", object.variables.get("temperature").firstRow().get("value"));

        driver.readPoints(Map.of("temperature", "sensors/temp"));
        assertEquals("24.1", object.variables.get("temperature").firstRow().get("value"));
    }

    @Test
    void missingTopicReturnsEmptyString() throws Exception {
        broker = new FakePulsarBroker();
        broker.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(broker.port())
        ));
        driver = new PulsarDeviceDriver();
        driver.initialize(object);
        driver.connect();

        driver.readPoints(Map.of("missing", "no-such-topic"));
        assertEquals("", object.variables.get("missing").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectThrows() {
        driver = new PulsarDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("t", "topic")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void metadataIdIsPulsar() {
        assertEquals("pulsar", new PulsarDeviceDriver().metadata().id());
        assertTrue(new PulsarDeviceDriver().metadata().supportsWrite());
    }

    private static final class FakePulsarBroker implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "fake-pulsar-broker");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, String> topics = new ConcurrentHashMap<>();

        FakePulsarBroker() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void put(String topic, String payload) {
            topics.put(topic, payload);
        }

        String get(String topic) {
            return topics.get(topic);
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
                    String line = readLine(in);
                    if (line == null) {
                        return;
                    }
                    out.write((reply(line) + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException ignored) {
            }
        }

        private String reply(String line) {
            if (line.startsWith("PUB ")) {
                String rest = line.substring(4);
                int space = rest.indexOf(' ');
                if (space < 0) {
                    return "ERR missing payload";
                }
                String topic = rest.substring(0, space);
                String payload = rest.substring(space + 1);
                topics.put(topic, payload);
                return "OK";
            }
            if (line.startsWith("GET ")) {
                String topic = line.substring(4).trim();
                String payload = topics.get(topic);
                if (payload == null) {
                    return "NIL";
                }
                return "MSG " + topic + " " + payload;
            }
            return "ERR unknown command";
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true) {
                int ch = in.read();
                if (ch < 0) {
                    if (line.size() == 0) {
                        return null;
                    }
                    break;
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
                    "test-pulsar",
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
