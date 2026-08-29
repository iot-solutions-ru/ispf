package com.ispf.driver.profibus;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * PROFIBUS protocol stub (profibus).
 * <p>
 * PROFIBUS DP/PA gateway stub (serial/fieldbus bridge required).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ProfibusDeviceDriver extends ProtocolStubDeviceDriver {

    public ProfibusDeviceDriver() {
        super(
                "profibus",
                "PROFIBUS Driver",
                "PROFIBUS DP/PA gateway stub (serial/fieldbus bridge required)",
                9600
        );
    }
}
