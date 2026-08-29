package com.ispf.driver.profibuspa;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * PROFIBUS PA protocol stub (profibus-pa).
 * <p>
 * PROFIBUS PA instrument network stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ProfibusPaDeviceDriver extends ProtocolStubDeviceDriver {

    public ProfibusPaDeviceDriver() {
        super(
                "profibus-pa",
                "PROFIBUS PA Driver",
                "PROFIBUS PA instrument network stub",
                9600
        );
    }
}
