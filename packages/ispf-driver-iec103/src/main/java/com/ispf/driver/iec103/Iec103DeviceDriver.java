package com.ispf.driver.iec103;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IEC 60870-5-103 protocol stub (iec103).
 * <p>
 * IEC 60870-5-103 protection stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class Iec103DeviceDriver extends ProtocolStubDeviceDriver {

    public Iec103DeviceDriver() {
        super(
                "iec103",
                "IEC 60870-5-103 Driver",
                "IEC 60870-5-103 protection stub",
                2404
        );
    }
}
