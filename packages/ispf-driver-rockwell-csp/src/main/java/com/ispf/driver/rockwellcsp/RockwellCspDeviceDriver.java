package com.ispf.driver.rockwellcsp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Rockwell CSP protocol stub (rockwell-csp).
 * <p>
 * Allen-Bradley CSP (legacy Ethernet) stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class RockwellCspDeviceDriver extends ProtocolStubDeviceDriver {

    public RockwellCspDeviceDriver() {
        super(
                "rockwell-csp",
                "Rockwell CSP Driver",
                "Allen-Bradley CSP (legacy Ethernet) stub",
                2222
        );
    }
}
