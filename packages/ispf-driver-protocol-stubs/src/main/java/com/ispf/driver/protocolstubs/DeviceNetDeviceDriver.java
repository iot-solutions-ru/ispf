package com.ispf.driver.protocolstubs;

/**
 * DeviceNet protocol stub (device-net).
 * <p>
 * ODVA DeviceNet gateway stub.
 */
public class DeviceNetDeviceDriver extends ProtocolStubDeviceDriver {

    public DeviceNetDeviceDriver() {
        super(
                "device-net",
                "DeviceNet Driver",
                "ODVA DeviceNet gateway stub",
                44818
        );
    }
}
