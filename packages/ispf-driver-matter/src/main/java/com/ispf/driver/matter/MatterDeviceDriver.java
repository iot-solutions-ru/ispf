package com.ispf.driver.matter;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Matter protocol stub (matter).
 * <p>
 * Matter / CHIP controller stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class MatterDeviceDriver extends ProtocolStubDeviceDriver {

    public MatterDeviceDriver() {
        super(
                "matter",
                "Matter Driver",
                "Matter / CHIP controller stub",
                5540
        );
    }
}
