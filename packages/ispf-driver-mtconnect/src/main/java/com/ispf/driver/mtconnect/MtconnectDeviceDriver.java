package com.ispf.driver.mtconnect;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * MTConnect protocol stub (mtconnect).
 * <p>
 * MTConnect agent HTTP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class MtconnectDeviceDriver extends ProtocolStubDeviceDriver {

    public MtconnectDeviceDriver() {
        super(
                "mtconnect",
                "MTConnect Driver",
                "MTConnect agent HTTP stub",
                5000
        );
    }
}
