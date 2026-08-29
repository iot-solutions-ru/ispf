package com.ispf.driver.protocolstubs;

/**
 * IEC 60870-5-101 protocol stub (iec101).
 * <p>
 * IEC 60870-5-101 serial/TCP stub.
 */
public class Iec101DeviceDriver extends ProtocolStubDeviceDriver {

    public Iec101DeviceDriver() {
        super(
                "iec101",
                "IEC 60870-5-101 Driver",
                "IEC 60870-5-101 serial/TCP stub",
                2404
        );
    }
}
