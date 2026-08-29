package com.ispf.driver.iec101;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IEC 60870-5-101 protocol stub (iec101).
 * <p>
 * IEC 60870-5-101 serial/TCP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
