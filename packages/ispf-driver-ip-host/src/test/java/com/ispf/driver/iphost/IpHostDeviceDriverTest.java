package com.ispf.driver.iphost;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpHostDeviceDriverTest {

    private HttpServer httpServer;
    private ServerSocket tcpServer;

    @AfterEach
    void tearDown() throws Exception {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (tcpServer != null) {
            tcpServer.close();
            tcpServer = null;
        }
    }

    @Test
    void checksAllModesViaLoopback() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        httpServer.start();
        tcpServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));

        StubDriverObject driverObject = new StubDriverObject(Map.of("timeoutMs", "2000"));
        IpHostDeviceDriver driver = new IpHostDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "web", "HTTP:127.0.0.1:" + httpServer.getAddress().getPort(),
                "port", "TCP:127.0.0.1:" + tcpServer.getLocalPort(),
                "name", "DNS:localhost",
                "ping", "PING:127.0.0.1"
        ));

        DataRecord web = driverObject.variables.get("web");
        assertEquals(Boolean.TRUE, web.firstRow().get("reachable"));
        assertEquals("200", web.firstRow().get("value"));

        DataRecord port = driverObject.variables.get("port");
        assertEquals(Boolean.TRUE, port.firstRow().get("reachable"));
        assertEquals("open", port.firstRow().get("value"));

        DataRecord name = driverObject.variables.get("name");
        assertEquals(Boolean.TRUE, name.firstRow().get("reachable"));
        assertEquals(InetAddress.getByName("localhost").getHostAddress(), name.firstRow().get("value"));

        DataRecord ping = driverObject.variables.get("ping");
        assertEquals(Boolean.TRUE, ping.firstRow().get("reachable"));
        assertEquals("reachable", ping.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        IpHostDeviceDriver driver = new IpHostDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("web", DataRecord.single(
                        DataSchema.builder("value")
                                .field("value", FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-ip-host",
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
