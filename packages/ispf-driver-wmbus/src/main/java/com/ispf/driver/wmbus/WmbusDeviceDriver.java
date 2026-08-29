package com.ispf.driver.wmbus;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Wireless M-Bus protocol stub (wmbus).
 * <p>
 * Wireless M-Bus (OMS) stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class WmbusDeviceDriver extends ProtocolStubDeviceDriver {

    public WmbusDeviceDriver() {
        super(
                "wmbus",
                "Wireless M-Bus Driver",
                "Wireless M-Bus (OMS) stub",
                10000
        );
    }
}
