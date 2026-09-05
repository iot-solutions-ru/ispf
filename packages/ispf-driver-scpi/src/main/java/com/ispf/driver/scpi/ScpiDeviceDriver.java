package com.ispf.driver.scpi;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * SCPI protocol stub (scpi).
 * <p>
 * IEEE 488.2 SCPI instrument stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ScpiDeviceDriver extends ProtocolStubDeviceDriver {

    public ScpiDeviceDriver() {
        super(
                "scpi",
                "SCPI Driver",
                "IEEE 488.2 SCPI instrument stub",
                5025
        );
    }
}
