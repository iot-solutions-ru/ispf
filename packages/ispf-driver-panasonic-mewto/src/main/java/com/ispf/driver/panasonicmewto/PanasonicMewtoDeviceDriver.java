package com.ispf.driver.panasonicmewto;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Panasonic MEWTOCOL protocol stub (panasonic-mewto).
 * <p>
 * Panasonic MEWTOCOL-COM/DAT stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class PanasonicMewtoDeviceDriver extends ProtocolStubDeviceDriver {

    public PanasonicMewtoDeviceDriver() {
        super(
                "panasonic-mewto",
                "Panasonic MEWTOCOL Driver",
                "Panasonic MEWTOCOL-COM/DAT stub",
                9094
        );
    }
}
