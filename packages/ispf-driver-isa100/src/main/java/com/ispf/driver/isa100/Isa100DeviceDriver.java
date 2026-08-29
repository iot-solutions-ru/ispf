package com.ispf.driver.isa100;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * ISA100.11a protocol stub (isa100).
 * <p>
 * ISA100 wireless gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class Isa100DeviceDriver extends ProtocolStubDeviceDriver {

    public Isa100DeviceDriver() {
        super(
                "isa100",
                "ISA100.11a Driver",
                "ISA100 wireless gateway stub",
                5094
        );
    }
}
