package com.ispf.driver.weighbridge;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Weighbridge protocol stub (weighbridge).
 * <p>
 * Truck scale / weighbridge protocol stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class WeighbridgeDeviceDriver extends ProtocolStubDeviceDriver {

    public WeighbridgeDeviceDriver() {
        super(
                "weighbridge",
                "Weighbridge Driver",
                "Truck scale / weighbridge protocol stub",
                4001
        );
    }
}
