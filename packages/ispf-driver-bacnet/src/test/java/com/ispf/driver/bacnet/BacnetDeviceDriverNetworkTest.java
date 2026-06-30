package com.ispf.driver.bacnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.test.TestNetwork;
import com.serotonin.bacnet4j.npdu.test.TestNetworkMap;
import com.serotonin.bacnet4j.obj.AnalogValueObject;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link BacnetDeviceDriver} read/write through in-memory TestNetwork (CI-safe, BL-30).
 */
class BacnetDeviceDriverNetworkTest {

    private static final int SERVER_NODE = 1;
    private static final int CLIENT_NODE = 2;
    private static final int SERVER_DEVICE_ID = 1001;
    private static final int CLIENT_DEVICE_ID = 2002;
    private static final DataSchema WRITE_SCHEMA = DataSchema.builder("writeValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private TestNetworkMap networkMap;
    private TestNetwork serverNetwork;
    private TestNetwork clientNetwork;
    private LocalDevice serverDevice;
    private LocalDevice clientDevice;

    @AfterEach
    void tearDown() throws Exception {
        if (clientDevice != null) {
            clientDevice.terminate();
            clientDevice = null;
        }
        if (serverDevice != null) {
            serverDevice.terminate();
            serverDevice = null;
        }
        serverNetwork = null;
        clientNetwork = null;
        networkMap = null;
    }

    @Test
    @Timeout(10)
    void driverReadsAndWritesPresentValueOverTestNetwork() throws Exception {
        networkMap = new TestNetworkMap();
        serverNetwork = new TestNetwork(networkMap, SERVER_NODE, 0);
        clientNetwork = new TestNetwork(networkMap, CLIENT_NODE, 0);

        serverDevice = new LocalDevice(SERVER_DEVICE_ID, new DefaultTransport(serverNetwork));
        clientDevice = new LocalDevice(CLIENT_DEVICE_ID, new DefaultTransport(clientNetwork));
        serverDevice.initialize();
        clientDevice.initialize();

        new AnalogValueObject(
                serverDevice,
                1,
                "setpoint",
                18.5f,
                EngineeringUnits.noUnits,
                false
        ).supportWritable();

        RemoteDevice remote = clientDevice.getRemoteDeviceBlocking(SERVER_DEVICE_ID, 5000);

        BacnetDeviceDriverTest.StubDriverObject driverObject = new BacnetDeviceDriverTest.StubDriverObject(Map.of());
        BacnetDeviceDriver driver = new BacnetDeviceDriver();
        driver.initialize(driverObject);
        driver.attachTestDevices(clientDevice, remote);

        driver.readPoints(Map.of("setpoint", "analog-value:1:present-value"));
        DataRecord initial = driverObject.variables.get("setpoint");
        assertTrue(initial.firstRow().get("value").toString().contains("18.5"));

        driver.writePoint("setpoint", DataRecord.single(WRITE_SCHEMA, Map.of("value", 27.25)));
        DataRecord updated = driverObject.variables.get("setpoint");
        assertTrue(updated.firstRow().get("value").toString().contains("27.25"));

        driver.disconnect();
    }
}
