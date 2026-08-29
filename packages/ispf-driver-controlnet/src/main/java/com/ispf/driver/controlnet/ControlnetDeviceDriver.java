package com.ispf.driver.controlnet;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * ControlNet protocol stub (controlnet).
 * <p>
 * ODVA ControlNet gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ControlnetDeviceDriver extends ProtocolStubDeviceDriver {

    public ControlnetDeviceDriver() {
        super(
                "controlnet",
                "ControlNet Driver",
                "ODVA ControlNet gateway stub",
                2222
        );
    }
}
