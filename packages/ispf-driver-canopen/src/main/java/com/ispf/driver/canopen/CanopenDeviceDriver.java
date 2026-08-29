package com.ispf.driver.canopen;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * CANopen protocol stub (canopen).
 * <p>
 * CANopen / CAN gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
