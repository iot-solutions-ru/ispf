package com.ispf.server.driver;

import com.ispf.driver.dnp3.Dnp3DeviceDriver;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Dnp3DriverUnitTest {

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

    @Test
    void maturityIsBeta() {
        assertEquals(com.ispf.driver.DriverMaturity.BETA, DriverMaturityRegistry.resolve("dnp3"));
    }
}
