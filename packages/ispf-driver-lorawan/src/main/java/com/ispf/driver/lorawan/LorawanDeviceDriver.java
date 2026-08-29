package com.ispf.driver.lorawan;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * LoRaWAN protocol stub (lorawan).
 * <p>
 * LoRaWAN network/application server gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class LorawanDeviceDriver extends ProtocolStubDeviceDriver {

    public LorawanDeviceDriver() {
        super(
                "lorawan",
                "LoRaWAN Driver",
                "LoRaWAN network/application server gateway stub",
                1700
        );
    }
}
