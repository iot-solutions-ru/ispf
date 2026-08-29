package com.ispf.driver.fujisph;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Fuji SPH protocol stub (fuji-sph).
 * <p>
 * Fuji Electric SPH / MICREX stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class FujiSphDeviceDriver extends ProtocolStubDeviceDriver {

    public FujiSphDeviceDriver() {
        super(
                "fuji-sph",
                "Fuji SPH Driver",
                "Fuji Electric SPH / MICREX stub",
                507
        );
    }
}
