package com.ispf.driver.protocolstubs;

/**
 * Weighbridge protocol stub (weighbridge).
 * <p>
 * Truck scale / weighbridge protocol stub.
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
