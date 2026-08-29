package com.ispf.driver.eebus;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * EEBUS protocol stub (eebus).
 * <p>
 * EEBUS / SHIP energy management stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class EebusDeviceDriver extends ProtocolStubDeviceDriver {

    public EebusDeviceDriver() {
        super(
                "eebus",
                "EEBUS Driver",
                "EEBUS / SHIP energy management stub",
                4712
        );
    }
}
