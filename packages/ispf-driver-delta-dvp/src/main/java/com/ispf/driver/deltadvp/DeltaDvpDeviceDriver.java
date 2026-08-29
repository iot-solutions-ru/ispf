package com.ispf.driver.deltadvp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Delta DVP protocol stub (delta-dvp).
 * <p>
 * Delta DVP / AS series PLC stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class DeltaDvpDeviceDriver extends ProtocolStubDeviceDriver {

    public DeltaDvpDeviceDriver() {
        super(
                "delta-dvp",
                "Delta DVP Driver",
                "Delta DVP / AS series PLC stub",
                502
        );
    }
}
