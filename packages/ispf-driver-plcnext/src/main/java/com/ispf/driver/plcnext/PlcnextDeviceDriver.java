package com.ispf.driver.plcnext;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * PLCnext protocol stub (plcnext).
 * <p>
 * Phoenix Contact PLCnext Engineer/RSC stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class PlcnextDeviceDriver extends ProtocolStubDeviceDriver {

    public PlcnextDeviceDriver() {
        super(
                "plcnext",
                "PLCnext Driver",
                "Phoenix Contact PLCnext Engineer/RSC stub",
                41100
        );
    }
}
