package com.ispf.driver.dnp3;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Dnp3DeviceDriverTest {

    @Test
    void connectsToTcpServerAndReportsStatus() throws Exception {
        CountDownLatch listening = new CountDownLatch(1);
        AtomicReference<Integer> boundPort = new AtomicReference<>();
        Thread server = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                boundPort.set(serverSocket.getLocalPort());
                listening.countDown();
                try (Socket client = serverSocket.accept()) {
                    Thread.sleep(2000);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        server.setDaemon(true);
        server.start();
        assertTrue(listening.await(5, TimeUnit.SECONDS));
        assertTrue(boundPort.get() != null && boundPort.get() > 0);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(boundPort.get()),
                "timeoutMs", "3000"
        ));
        Dnp3DeviceDriver driver = new Dnp3DeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
        driver.readPoints(Map.of("analog0", "0:ANALOG_INPUT"));
        assertEquals("TCP_CONNECTED", driverObject.lastStatus());
        driver.disconnect();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
                .field("value", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();
        private String lastStatus;

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-device",
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
            Object status = value.firstRow().get("status");
            if (status != null) {
                lastStatus = status.toString();
            }
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            if (configuration.containsKey(name)) {
                String value = configuration.get(name);
                return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
            }
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }

        String lastStatus() {
            return lastStatus;
        }
    }
}
