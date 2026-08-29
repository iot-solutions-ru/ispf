package com.ispf.driver.iolink;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IO-Link protocol stub (io-link).
 * <p>
 * IO-Link master REST/MQTT bridge stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class IoLinkDeviceDriver extends ProtocolStubDeviceDriver {

    public IoLinkDeviceDriver() {
        super(
                "io-link",
                "IO-Link Driver",
                "IO-Link master REST/MQTT bridge stub",
                8080
        );
    }
}
