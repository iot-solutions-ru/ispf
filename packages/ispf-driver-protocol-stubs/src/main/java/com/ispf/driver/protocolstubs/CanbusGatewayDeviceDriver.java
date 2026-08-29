package com.ispf.driver.protocolstubs;

/**
 * CAN bus gateway protocol stub (canbus-gateway).
 * <p>
 * Generic CAN/CAN-FD TCP gateway stub.
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
