package com.ispf.driver.hartserial;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * HART serial protocol stub (hart-serial).
 * <p>
 * HART FSK serial/modem stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class HartSerialDeviceDriver extends ProtocolStubDeviceDriver {

    public HartSerialDeviceDriver() {
        super(
                "hart-serial",
                "HART serial Driver",
                "HART FSK serial/modem stub",
                5094
        );
    }
}
