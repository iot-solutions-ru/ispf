package com.ispf.driver.hitachihidic;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Hitachi HIDIC protocol stub (hitachi-hidic).
 * <p>
 * Hitachi HIDIC / EH-150 stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class HitachiHidicDeviceDriver extends ProtocolStubDeviceDriver {

    public HitachiHidicDeviceDriver() {
        super(
                "hitachi-hidic",
                "Hitachi HIDIC Driver",
                "Hitachi HIDIC / EH-150 stub",
                3000
        );
    }
}
