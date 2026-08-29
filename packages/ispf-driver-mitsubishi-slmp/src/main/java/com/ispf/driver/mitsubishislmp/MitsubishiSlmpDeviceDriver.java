package com.ispf.driver.mitsubishislmp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Mitsubishi SLMP protocol stub (mitsubishi-slmp).
 * <p>
 * Mitsubishi SLMP (Seamless Message Protocol) stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class MitsubishiSlmpDeviceDriver extends ProtocolStubDeviceDriver {

    public MitsubishiSlmpDeviceDriver() {
        super(
                "mitsubishi-slmp",
                "Mitsubishi SLMP Driver",
                "Mitsubishi SLMP (Seamless Message Protocol) stub",
                5007
        );
    }
}
