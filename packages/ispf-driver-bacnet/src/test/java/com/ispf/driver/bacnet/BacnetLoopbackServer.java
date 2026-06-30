package com.ispf.driver.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.obj.AnalogValueObject;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.primitive.OctetString;
import com.serotonin.bacnet4j.type.primitive.Unsigned16;

import java.net.InetAddress;

/**
 * In-process BACnet/IP device for loopback driver tests.
 */
final class BacnetLoopbackServer implements AutoCloseable {

    static final String LOOPBACK_HOST = "127.0.0.1";

    private final LocalDevice localDevice;

    BacnetLoopbackServer(int deviceId, int port, float initialValue) throws Exception {
        this(deviceId, LOOPBACK_HOST, port, initialValue);
    }

    BacnetLoopbackServer(int deviceId, String bindHost, int port, float initialValue) throws Exception {
        IpNetwork network = new IpNetworkBuilder()
                .withLocalBindAddress(bindHost)
                .withSubnet("127.0.0.0", 8)
                .withBroadcast("127.0.0.255", 8)
                .withPort(port)
                .withReuseAddress(true)
                .build();
        localDevice = new LocalDevice(deviceId, new DefaultTransport(network));
        localDevice.initialize();
        new AnalogValueObject(
                localDevice,
                1,
                "setpoint",
                initialValue,
                EngineeringUnits.noUnits,
                false
        ).supportWritable();
    }

    LocalDevice localDevice() {
        return localDevice;
    }

    static Address toBacnetAddress(String host, int port) throws Exception {
        byte[] ip = InetAddress.getByName(host).getAddress();
        byte[] mac = new byte[] {
                ip[0], ip[1], ip[2], ip[3],
                (byte) (port >> 8),
                (byte) port
        };
        return new Address(new Unsigned16(0), new OctetString(mac));
    }

    @Override
    public void close() {
        if (localDevice != null) {
            try {
                localDevice.terminate();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
