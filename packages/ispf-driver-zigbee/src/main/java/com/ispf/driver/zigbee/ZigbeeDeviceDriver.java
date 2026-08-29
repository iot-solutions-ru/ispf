package com.ispf.driver.zigbee;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Zigbee protocol stub (zigbee).
 * <p>
 * Zigbee coordinator / ZCL stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ZigbeeDeviceDriver extends ProtocolStubDeviceDriver {

    public ZigbeeDeviceDriver() {
        super(
                "zigbee",
                "Zigbee Driver",
                "Zigbee coordinator / ZCL stub",
                17754
        );
    }
}
