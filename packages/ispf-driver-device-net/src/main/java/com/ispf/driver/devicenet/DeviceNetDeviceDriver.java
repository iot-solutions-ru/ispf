package com.ispf.driver.devicenet;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * DeviceNet protocol stub (device-net).
 * <p>
 * ODVA DeviceNet gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
