package com.ispf.driver.wirelesshart;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * WirelessHART protocol stub (wirelesshart).
 * <p>
 * WirelessHART gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class WirelesshartDeviceDriver extends ProtocolStubDeviceDriver {

    public WirelesshartDeviceDriver() {
        super(
                "wirelesshart",
                "WirelessHART Driver",
                "WirelessHART gateway stub",
                5094
        );
    }
}
