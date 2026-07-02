package com.ispf.driver.gpstracker;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class GpsTrackerDeviceDriverTest {

    private GpsTrackerDeviceDriver driver;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
        }
    }

    @Test
    void connectedClientsReflectOpenAndClosedSockets() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        AtomicReference<DataRecord> lastRecord = new AtomicReference<>();
        DeviceDriver.DriverObject driverObject = new DeviceDriver.DriverObject() {
            @Override
            public PlatformObject deviceObject() {
                return null;
            }

            @Override
            public void updateVariable(String name, DataRecord value) {
                lastRecord.set(value);
            }

            @Override
            public Optional<DataRecord> getVariable(String name) {
                return Optional.empty();
            }

            @Override
            public void log(DeviceDriver.DriverLogLevel level, String message) {
                // no-op
            }

            @Override
            public Map<String, String> configuration() {
                return Map.of(
                        "listenPort", String.valueOf(port),
                        "bufferSize", "64"
                );
            }
        };

        driver = new GpsTrackerDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        try (Socket first = new Socket("127.0.0.1", port);
             Socket second = new Socket("127.0.0.1", port);
             Socket third = new Socket("127.0.0.1", port)) {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> awaitConnectedClients(3, lastRecord));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> awaitConnectedClients(0, lastRecord));
    }

    private void awaitConnectedClients(int expected, AtomicReference<DataRecord> lastRecord) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            driver.readPoints(Map.of("gps.last", "feed"));
            DataRecord record = lastRecord.get();
            if (record != null) {
                Object value = record.firstRow().get("connectedClients");
                if (value instanceof Integer integer && integer == expected) {
                    return;
                }
            }
            Thread.sleep(25);
        }
        driver.readPoints(Map.of("gps.last", "feed"));
        DataRecord record = lastRecord.get();
        Object value = record == null ? null : record.firstRow().get("connectedClients");
        assertEquals(expected, value);
    }
}
