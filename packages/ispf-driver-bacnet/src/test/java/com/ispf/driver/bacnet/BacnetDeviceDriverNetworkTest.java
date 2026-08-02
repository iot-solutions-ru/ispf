package com.ispf.driver.bacnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.bacnet.codec.BacnetEngineeringUnit;
import com.ispf.driver.bacnet.codec.BacnetObjectIdentifier;
import com.ispf.driver.bacnet.codec.BacnetObjectType;
import com.ispf.driver.bacnet.codec.BacnetValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link BacnetDeviceDriver} read/write through the owned UDP loopback device.
 */
class BacnetDeviceDriverNetworkTest {

    private static final int SERVER_DEVICE_ID = 1001;
    private static final int CLIENT_DEVICE_ID = 2002;
    private static final DataSchema WRITE_SCHEMA = DataSchema.builder("writeValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private BacnetLoopbackServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    @Timeout(10)
    void driverReadsAndWritesPresentValueOverUdp() throws Exception {
        int serverPort = freePort();
        int clientBindPort = freePort();
        server = new BacnetLoopbackServer(SERVER_DEVICE_ID, serverPort, 18.5f);
        server.addAnalogValue(1, 18.5f, BacnetEngineeringUnit.DEGREES_CELSIUS, true);

        BacnetDeviceDriverTest.StubDriverObject driverObject = driverObject(serverPort, clientBindPort, Map.of());
        BacnetDeviceDriver driver = connect(driverObject);

        driver.readPoints(Map.of("setpoint", "analog-value:1:present-value"));
        DataRecord initial = driverObject.variables.get("setpoint");
        assertTrue(initial.firstRow().get("value").toString().contains("18.5"));
        assertEquals("°C", initial.firstRow().get("unit"));

        driver.writePoint("setpoint", DataRecord.single(WRITE_SCHEMA, Map.of("value", 27.25)));
        DataRecord updated = driverObject.variables.get("setpoint");
        assertTrue(updated.firstRow().get("value").toString().contains("27.25"));
        BacnetValue stored = server.read(new BacnetObjectIdentifier(BacnetObjectType.ANALOG_VALUE, 1));
        BacnetValue.RealValue real = assertInstanceOf(BacnetValue.RealValue.class, stored);
        assertEquals(27.25f, real.value(), 0.001f);

        driver.disconnect();
    }

    @Test
    @Timeout(10)
    void discoverRemoteDeviceViaWhoIsOverUdp() throws Exception {
        int serverPort = freePort();
        int clientBindPort = freePort();
        server = new BacnetLoopbackServer(SERVER_DEVICE_ID, serverPort, 12.0f);

        BacnetDeviceDriverTest.StubDriverObject driverObject = driverObject(serverPort, clientBindPort, Map.of(
                "discoveryMode", "whoIs"
        ));
        BacnetDeviceDriver driver = connect(driverObject);

        assertTrue(driver.isConnected());
        driver.readPoints(Map.of("setpoint", "analog-value:1:present-value"));
        assertTrue(driverObject.variables.get("setpoint").firstRow().get("value").toString().contains("12"));

        driver.disconnect();
    }

    @Test
    @Timeout(10)
    void readsBinaryPresentValues() throws Exception {
        int serverPort = freePort();
        int clientBindPort = freePort();
        server = new BacnetLoopbackServer(SERVER_DEVICE_ID, serverPort, 18.5f);
        server.addBinaryValue(2, true, true);
        server.addBinaryInput(3, false);

        BacnetDeviceDriverTest.StubDriverObject driverObject = driverObject(serverPort, clientBindPort, Map.of());
        BacnetDeviceDriver driver = connect(driverObject);

        driver.readPoints(Map.of(
                "pump", "binary-value:2:present-value",
                "contact", "binary-input:3:present-value"
        ));
        assertEquals("active", driverObject.variables.get("pump").firstRow().get("value"));
        assertEquals("inactive", driverObject.variables.get("contact").firstRow().get("value"));

        driver.disconnect();
    }

    private static BacnetDeviceDriver connect(BacnetDeviceDriverTest.StubDriverObject driverObject) throws Exception {
        BacnetDeviceDriver driver = new BacnetDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        return driver;
    }

    private static BacnetDeviceDriverTest.StubDriverObject driverObject(
            int serverPort,
            int clientBindPort,
            Map<String, String> overrides
    ) {
        Map<String, String> config = new java.util.HashMap<>();
        config.put("bindAddress", BacnetLoopbackServer.LOOPBACK_HOST);
        config.put("host", BacnetLoopbackServer.LOOPBACK_HOST);
        config.put("port", String.valueOf(serverPort));
        config.put("bindPort", String.valueOf(clientBindPort));
        config.put("localDeviceId", String.valueOf(CLIENT_DEVICE_ID));
        config.put("remoteDeviceId", String.valueOf(SERVER_DEVICE_ID));
        config.put("timeoutMs", "5000");
        config.putAll(overrides);
        return new BacnetDeviceDriverTest.StubDriverObject(config);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
