package com.ispf.driver.ieee20305;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IEEE 2030.5 protocol stub (ieee2030-5).
 * <p>
 * IEEE 2030.5 (SEP2) stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class Ieee20305DeviceDriver extends ProtocolStubDeviceDriver {

    public Ieee20305DeviceDriver() {
        super(
                "ieee2030-5",
                "IEEE 2030.5 Driver",
                "IEEE 2030.5 (SEP2) stub",
                443
        );
    }
}
