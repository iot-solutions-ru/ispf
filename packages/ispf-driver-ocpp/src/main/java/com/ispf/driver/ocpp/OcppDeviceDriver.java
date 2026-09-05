package com.ispf.driver.ocpp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * OCPP protocol stub (ocpp).
 * <p>
 * Open Charge Point Protocol (CSMS) stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class OcppDeviceDriver extends ProtocolStubDeviceDriver {

    public OcppDeviceDriver() {
        super(
                "ocpp",
                "OCPP Driver",
                "Open Charge Point Protocol (CSMS) stub",
                9000
        );
    }
}
