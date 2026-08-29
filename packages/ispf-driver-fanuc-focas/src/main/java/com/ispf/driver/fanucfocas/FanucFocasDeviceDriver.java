package com.ispf.driver.fanucfocas;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Fanuc FOCAS protocol stub (fanuc-focas).
 * <p>
 * Fanuc FOCAS CNC stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class FanucFocasDeviceDriver extends ProtocolStubDeviceDriver {

    public FanucFocasDeviceDriver() {
        super(
                "fanuc-focas",
                "Fanuc FOCAS Driver",
                "Fanuc FOCAS CNC stub",
                8193
        );
    }
}
