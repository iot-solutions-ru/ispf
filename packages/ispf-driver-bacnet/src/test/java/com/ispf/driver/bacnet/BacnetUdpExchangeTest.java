package com.ispf.driver.bacnet;

import com.ispf.driver.bacnet.codec.BacnetIpClient;
import com.ispf.driver.bacnet.codec.BacnetObjectIdentifier;
import com.ispf.driver.bacnet.codec.BacnetObjectType;
import com.ispf.driver.bacnet.codec.BacnetPropertyIdentifier;
import com.ispf.driver.bacnet.codec.BacnetValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Direct UDP exchange test for the ISPF BACnet/IP codec and loopback device.
 */
class BacnetUdpExchangeTest {

    private static final int SERVER_DEVICE_ID = 1001;
    private BacnetLoopbackServer server;
    private BacnetIpClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
            client = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    @Timeout(10)
    void readsAndWritesPresentValueOverUdp() throws Exception {
        int serverPort = freePort();
        int clientPort = freePort();
        server = new BacnetLoopbackServer(SERVER_DEVICE_ID, serverPort, 18.5f);
        client = new BacnetIpClient(
                BacnetLoopbackServer.LOOPBACK_HOST,
                clientPort,
                BacnetLoopbackServer.LOOPBACK_HOST,
                serverPort,
                5000
        );
        client.discoverRemoteDevice(SERVER_DEVICE_ID);

        BacnetObjectIdentifier objectId = new BacnetObjectIdentifier(BacnetObjectType.ANALOG_VALUE, 1);
        BacnetValue initial = client.readProperty(objectId, BacnetPropertyIdentifier.PRESENT_VALUE);
        BacnetValue.RealValue initialReal = assertInstanceOf(BacnetValue.RealValue.class, initial);
        assertEquals(18.5f, initialReal.value(), 0.001f);

        client.writeProperty(objectId, BacnetPropertyIdentifier.PRESENT_VALUE, new BacnetValue.RealValue(27.25f));
        BacnetValue updated = client.readProperty(objectId, BacnetPropertyIdentifier.PRESENT_VALUE);
        BacnetValue.RealValue updatedReal = assertInstanceOf(BacnetValue.RealValue.class, updated);
        assertEquals(27.25f, updatedReal.value(), 0.001f);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
