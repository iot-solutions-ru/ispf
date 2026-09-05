package com.ispf.driver.weatherstation;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
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

class WeatherStationDeviceDriverTest {

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
    void metadataIsProductionReadOnly() {
        driver = new WeatherStationDeviceDriver();
        assertEquals("weather-station", driver.metadata().id());
        assertEquals(DriverMaturity.PRODUCTION, driver.metadata().maturity());
        assertEquals(Set.of("read"), driver.metadata().capabilities());
    }

    @Test
    void getFieldsLoopback() throws Exception {
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

        driver.readPoints(Map.of(
                "temp", "TEMP",
                "hum", "HUM",
                "all", "ALL"
        ));
        assertEquals("21.5", object.variables.get("temp").firstRow().get("value"));
        assertEquals("55", object.variables.get("hum").firstRow().get("value"));
        assertTrue(String.valueOf(object.variables.get("all").firstRow().get("value")).contains("TEMP=21.5"));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("temp", object.variables.get("temp")));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("read-only"));
    }

    private static final class FakeStation implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-weather-station");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, String> values = new ConcurrentHashMap<>(Map.of(
                "TEMP", "21.5",
                "HUM", "55",
                "PRESS", "1013.2",
                "WIND", "3.2"
        ));

        FakeStation() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        }

        int port() {
            return serverSocket.getLocalPort();
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
                    String command = WeatherStationDeviceDriver.readLine(in);
                    if (command == null) {
                        break;
                    }
                    String upper = command.trim().toUpperCase(Locale.ROOT);
                    if (upper.equals("GET ALL") || upper.equals("GET *")) {
                        StringBuilder line = new StringBuilder();
                        values.forEach((k, v) -> {
                            if (!line.isEmpty()) {
                                line.append(' ');
                            }
                            line.append(k).append('=').append(v);
                        });
                        WeatherStationDeviceDriver.writeLine(out, line.toString());
                    } else if (upper.startsWith("GET ")) {
                        String field = upper.substring(4).trim();
                        String value = values.getOrDefault(field, "");
                        WeatherStationDeviceDriver.writeLine(out, field + "=" + value);
                    } else {
                        WeatherStationDeviceDriver.writeLine(out, "ERR");
                    }
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
