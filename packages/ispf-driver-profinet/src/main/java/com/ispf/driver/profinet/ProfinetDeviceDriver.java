package com.ispf.driver.profinet;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * PROFINET IO protocol stub (profinet).
 * <p>
 * PROFINET IO controller/device stub (DCP/RPC not implemented).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ProfinetDeviceDriver extends ProtocolStubDeviceDriver {

    public ProfinetDeviceDriver() {
        super(
                "profinet",
                "PROFINET IO Driver",
                "PROFINET IO controller/device stub (DCP/RPC not implemented)",
                34964
        );
    }
}
