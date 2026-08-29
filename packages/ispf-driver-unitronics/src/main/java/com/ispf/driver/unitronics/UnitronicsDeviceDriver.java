package com.ispf.driver.unitronics;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Unitronics protocol stub (unitronics).
 * <p>
 * Unitronics PCOM stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class UnitronicsDeviceDriver extends ProtocolStubDeviceDriver {

    public UnitronicsDeviceDriver() {
        super(
                "unitronics",
                "Unitronics Driver",
                "Unitronics PCOM stub",
                20256
        );
    }
}
