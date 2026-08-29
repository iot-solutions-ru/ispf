package com.ispf.driver.wago;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * WAGO protocol stub (wago).
 * <p>
 * WAGO PFC / e!COCKPIT stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class WagoDeviceDriver extends ProtocolStubDeviceDriver {

    public WagoDeviceDriver() {
        super(
                "wago",
                "WAGO Driver",
                "WAGO PFC / e!COCKPIT stub",
                2455
        );
    }
}
