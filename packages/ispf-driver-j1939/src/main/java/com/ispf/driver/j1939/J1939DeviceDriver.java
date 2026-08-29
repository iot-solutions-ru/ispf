package com.ispf.driver.j1939;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * SAE J1939 protocol stub (j1939).
 * <p>
 * SAE J1939 vehicle network stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class J1939DeviceDriver extends ProtocolStubDeviceDriver {

    public J1939DeviceDriver() {
        super(
                "j1939",
                "SAE J1939 Driver",
                "SAE J1939 vehicle network stub",
                29536
        );
    }
}
