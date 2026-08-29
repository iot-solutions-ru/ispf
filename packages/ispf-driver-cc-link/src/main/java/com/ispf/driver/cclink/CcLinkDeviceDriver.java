package com.ispf.driver.cclink;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * CC-Link protocol stub (cc-link).
 * <p>
 * Mitsubishi CC-Link field network stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class CcLinkDeviceDriver extends ProtocolStubDeviceDriver {

    public CcLinkDeviceDriver() {
        super(
                "cc-link",
                "CC-Link Driver",
                "Mitsubishi CC-Link field network stub",
                5001
        );
    }
}
