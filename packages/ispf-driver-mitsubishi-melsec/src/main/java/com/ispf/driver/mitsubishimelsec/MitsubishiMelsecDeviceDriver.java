package com.ispf.driver.mitsubishimelsec;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Mitsubishi MELSEC protocol stub (mitsubishi-melsec).
 * <p>
 * Mitsubishi MELSEC communication stub (MC Protocol / SLMP path planned).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class MitsubishiMelsecDeviceDriver extends ProtocolStubDeviceDriver {

    public MitsubishiMelsecDeviceDriver() {
        super(
                "mitsubishi-melsec",
                "Mitsubishi MELSEC Driver",
                "Mitsubishi MELSEC communication stub (MC Protocol / SLMP path planned)",
                5007
        );
    }
}
