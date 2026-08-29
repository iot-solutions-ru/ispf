package com.ispf.driver.fatek;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Fatek FACON protocol stub (fatek).
 * <p>
 * Fatek FACON protocol stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class FatekDeviceDriver extends ProtocolStubDeviceDriver {

    public FatekDeviceDriver() {
        super(
                "fatek",
                "Fatek FACON Driver",
                "Fatek FACON protocol stub",
                500
        );
    }
}
