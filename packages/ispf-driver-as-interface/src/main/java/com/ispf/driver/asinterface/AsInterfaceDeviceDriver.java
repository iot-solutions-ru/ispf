package com.ispf.driver.asinterface;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * AS-Interface protocol stub (as-interface).
 * <p>
 * AS-Interface master/gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class AsInterfaceDeviceDriver extends ProtocolStubDeviceDriver {

    public AsInterfaceDeviceDriver() {
        super(
                "as-interface",
                "AS-Interface Driver",
                "AS-Interface master/gateway stub",
                9600
        );
    }
}
