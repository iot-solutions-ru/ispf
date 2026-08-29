package com.ispf.driver.cclinkie;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * CC-Link IE protocol stub (cc-link-ie).
 * <p>
 * Mitsubishi CC-Link IE Field/Control stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class CcLinkIeDeviceDriver extends ProtocolStubDeviceDriver {

    public CcLinkIeDeviceDriver() {
        super(
                "cc-link-ie",
                "CC-Link IE Driver",
                "Mitsubishi CC-Link IE Field/Control stub",
                5001
        );
    }
}
