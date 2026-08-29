package com.ispf.driver.canbusgateway;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * CAN bus gateway protocol stub (canbus-gateway).
 * <p>
 * Generic CAN/CAN-FD TCP gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class CanbusGatewayDeviceDriver extends ProtocolStubDeviceDriver {

    public CanbusGatewayDeviceDriver() {
        super(
                "canbus-gateway",
                "CAN bus gateway Driver",
                "Generic CAN/CAN-FD TCP gateway stub",
                29536
        );
    }
}
