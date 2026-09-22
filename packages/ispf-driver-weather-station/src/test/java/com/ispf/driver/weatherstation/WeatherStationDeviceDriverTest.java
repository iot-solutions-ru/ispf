package com.ispf.driver.weatherstation;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.weatherstation.codec.WeatherStationCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherStationDeviceDriverTest {

    /** Handwritten Davis Vantage LOOP command: LOOP\\n */
    private static final byte[] LOOP_LITERAL = new byte[] { 0x4C, 0x4F, 0x4F, 0x50, 0x0A };

    /**
     * Truncated LOOP ack body after 0x06 — not the full 99-byte Vantage packet.
     * Starts with ASCII {@code LOO} as on a real console reply prefix.
     */
    private static final byte[] TRUNCATED_LOOP_ACK_PAYLOAD =
            new byte[] { 'L', 'O', 'O', 0x00, 0x15 };

    private WeatherStationDeviceDriver driver;
    private FakeStation station;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (station != null) {
            station.close();
            station = null;
        }
    }

    @Test
    void loopCommandMatchesHandwrittenLiteral() {
        assertArrayEquals(LOOP_LITERAL, WeatherStationDeviceDriver.buildLoopCommand());
        assertArrayEquals(LOOP_LITERAL, WeatherStationCodec.encodeLoopCommand());
    }

    @Test
    void metadataDescribesLoopOverTcpWithoutLab() {
        driver = new WeatherStationDeviceDriver();
        assertEquals("weather-station", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
        String description = driver.metadata().description().toLowerCase(Locale.ROOT);
        assertTrue(description.contains("loop"));
        assertTrue(description.contains("tcp"));
        assertTrue(description.contains("not a vaisala") || description.contains("not vaisala"));
        assertFalse(description.contains("lab"));
    }

    @Test
    void loopAckLoopback() throws Exception {
        station = new FakeStation();
        station.start();

        StubDriverObject object = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(station.port()),
                "timeoutMs", "2000"
        ));
        driver = new WeatherStationDeviceDriver();
        driver.initialize(object);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("loop", "LOOP"));
        assertArrayEquals(LOOP_LITERAL, station.lastCommand());
        String value = String.valueOf(object.variables.get("loop").firstRow().get("value"));
        assertTrue(value.startsWith("LOO"));
        assertEquals(new String(TRUNCATED_LOOP_ACK_PAYLOAD, StandardCharsets.US_ASCII), value);

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("loop", object.variables.get("loop")));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("read-only"));
    }

    private static final class FakeStation implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-weather-station");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<byte[]> lastCommand = new AtomicReference<>(new byte[0]);

        FakeStation() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        byte[] lastCommand() {
            return lastCommand.get();
        }

        void start() {
            executor.submit(this::acceptLoop);
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handle(socket));
                } catch (IOException ignored) {
                    return;
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                while (true) {
                    byte[] command = new byte[5];
                    int offset = 0;
                    while (offset < command.length) {
                        int n = in.read(command, offset, command.length - offset);
                        if (n < 0) {
                            return;
                        }
                        offset += n;
                    }
                    lastCommand.set(Arrays.copyOf(command, command.length));
                    if (!Arrays.equals(command, LOOP_LITERAL)) {
                        out.write(0x21); // '!' failure style
                        out.flush();
                        continue;
                    }
                    // ACK then truncated LOOP payload (documented short stand-in for the 99-byte packet)
                    out.write(WeatherStationCodec.ACK);
                    out.write(TRUNCATED_LOOP_ACK_PAYLOAD);
                    out.flush();
                }
            } catch (IOException ignored) {
                // closed
            }
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
        final Map<String, DataRecord> variables = new ConcurrentHashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject("test-ws", "root.platform.devices.test", ObjectType.DEVICE, "Test", "", null);
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
