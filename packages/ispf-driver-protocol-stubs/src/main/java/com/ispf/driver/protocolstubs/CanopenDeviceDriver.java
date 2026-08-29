package com.ispf.driver.protocolstubs;

/**
 * CANopen protocol stub (canopen).
 * <p>
 * CANopen / CAN gateway stub.
 */
public class CanopenDeviceDriver extends ProtocolStubDeviceDriver {

    public CanopenDeviceDriver() {
        super(
                "canopen",
                "CANopen Driver",
                "CANopen / CAN gateway stub",
                11898
        );
    }
}
