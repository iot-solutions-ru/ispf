package com.ispf.driver.uds;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * UDS (ISO 14229) protocol stub (uds).
 * <p>
 * Unified Diagnostic Services over DoIP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class UdsDeviceDriver extends ProtocolStubDeviceDriver {

    public UdsDeviceDriver() {
        super(
                "uds",
                "UDS (ISO 14229) Driver",
                "Unified Diagnostic Services over DoIP stub",
                13400
        );
    }
}
