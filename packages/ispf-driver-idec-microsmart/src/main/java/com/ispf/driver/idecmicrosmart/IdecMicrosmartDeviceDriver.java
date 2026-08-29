package com.ispf.driver.idecmicrosmart;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IDEC MicroSmart protocol stub (idec-microsmart).
 * <p>
 * IDEC MicroSmart FC6A stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class IdecMicrosmartDeviceDriver extends ProtocolStubDeviceDriver {

    public IdecMicrosmartDeviceDriver() {
        super(
                "idec-microsmart",
                "IDEC MicroSmart Driver",
                "IDEC MicroSmart FC6A stub",
                10000
        );
    }
}
