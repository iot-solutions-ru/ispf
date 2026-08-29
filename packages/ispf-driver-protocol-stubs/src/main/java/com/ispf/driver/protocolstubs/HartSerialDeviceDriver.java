package com.ispf.driver.protocolstubs;

/**
 * HART serial protocol stub (hart-serial).
 * <p>
 * HART FSK serial/modem stub.
 */
public class HartSerialDeviceDriver extends ProtocolStubDeviceDriver {

    public HartSerialDeviceDriver() {
        super(
                "hart-serial",
                "HART serial Driver",
                "HART FSK serial/modem stub",
                5094
        );
    }
}
