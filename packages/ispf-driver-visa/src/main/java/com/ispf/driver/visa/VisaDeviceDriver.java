package com.ispf.driver.visa;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * VISA protocol stub (visa).
 * <p>
 * IVI/VISA instrument resource stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class VisaDeviceDriver extends ProtocolStubDeviceDriver {

    public VisaDeviceDriver() {
        super(
                "visa",
                "VISA Driver",
                "IVI/VISA instrument resource stub",
                5025
        );
    }
}
