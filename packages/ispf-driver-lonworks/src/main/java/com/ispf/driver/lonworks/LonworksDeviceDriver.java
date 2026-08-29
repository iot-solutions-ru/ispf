package com.ispf.driver.lonworks;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * LonWorks protocol stub (lonworks).
 * <p>
 * LonWorks/LonTalk IP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class LonworksDeviceDriver extends ProtocolStubDeviceDriver {

    public LonworksDeviceDriver() {
        super(
                "lonworks",
                "LonWorks Driver",
                "LonWorks/LonTalk IP stub",
                1628
        );
    }
}
