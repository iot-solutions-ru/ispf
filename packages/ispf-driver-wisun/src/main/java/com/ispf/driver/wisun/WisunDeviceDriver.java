package com.ispf.driver.wisun;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Wi-SUN protocol stub (wisun).
 * <p>
 * Wi-SUN FAN border router stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class WisunDeviceDriver extends ProtocolStubDeviceDriver {

    public WisunDeviceDriver() {
        super(
                "wisun",
                "Wi-SUN Driver",
                "Wi-SUN FAN border router stub",
                5683
        );
    }
}
