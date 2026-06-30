package com.ispf.driver.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.test.TestNetwork;
import com.serotonin.bacnet4j.npdu.test.TestNetworkMap;
import com.serotonin.bacnet4j.obj.AnalogValueObject;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.RequestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-memory BACnet exchange via bacnet4j {@link TestNetwork} (no UDP). CI-safe complement to IP loopback smoke.
 */
class BacnetTestNetworkExchangeTest {

    private static final int SERVER_NODE = 1;
    private static final int CLIENT_NODE = 2;
    private static final int SERVER_DEVICE_ID = 1001;
    private static final int CLIENT_DEVICE_ID = 2002;

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
    void readsAndWritesPresentValueOverTestNetwork() throws Exception {
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

        ObjectIdentifier oid = new ObjectIdentifier(ObjectType.analogValue, 1);
        String initial = RequestUtils.getProperty(
                clientDevice,
                remote,
                oid,
                PropertyIdentifier.presentValue
        ).toString();
        assertTrue(initial.contains("18.5"));

        RequestUtils.writeProperty(
                clientDevice,
                remote,
                oid,
                PropertyIdentifier.presentValue,
                new com.serotonin.bacnet4j.type.primitive.Real(27.25f)
        );
        String updated = RequestUtils.getProperty(
                clientDevice,
                remote,
                oid,
                PropertyIdentifier.presentValue
        ).toString();
        assertTrue(updated.contains("27.25"));
    }
}
