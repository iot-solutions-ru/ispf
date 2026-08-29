package com.ispf.driver.schneiderumac;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Schneider Unity/Modicon protocol stub (schneider-umac).
 * <p>
 * Schneider Electric Unity/Modicon advanced services stub (beyond Modbus).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class SchneiderUmacDeviceDriver extends ProtocolStubDeviceDriver {

    public SchneiderUmacDeviceDriver() {
        super(
                "schneider-umac",
                "Schneider Unity/Modicon Driver",
                "Schneider Electric Unity/Modicon advanced services stub (beyond Modbus)",
                502
        );
    }
}
