package com.ispf.driver.interbus;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * INTERBUS protocol stub (interbus).
 * <p>
 * INTERBUS fieldbus gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class InterbusDeviceDriver extends ProtocolStubDeviceDriver {

    public InterbusDeviceDriver() {
        super(
                "interbus",
                "INTERBUS Driver",
                "INTERBUS fieldbus gateway stub",
                502
        );
    }
}
