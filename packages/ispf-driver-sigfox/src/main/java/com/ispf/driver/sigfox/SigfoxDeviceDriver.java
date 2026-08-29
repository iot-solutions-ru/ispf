package com.ispf.driver.sigfox;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Sigfox protocol stub (sigfox).
 * <p>
 * Sigfox backend callback stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class SigfoxDeviceDriver extends ProtocolStubDeviceDriver {

    public SigfoxDeviceDriver() {
        super(
                "sigfox",
                "Sigfox Driver",
                "Sigfox backend callback stub",
                443
        );
    }
}
