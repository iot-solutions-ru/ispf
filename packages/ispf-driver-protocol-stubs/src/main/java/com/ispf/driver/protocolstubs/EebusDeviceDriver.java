package com.ispf.driver.protocolstubs;

/**
 * EEBUS protocol stub (eebus).
 * <p>
 * EEBUS / SHIP energy management stub.
 */
public class EebusDeviceDriver extends ProtocolStubDeviceDriver {

    public EebusDeviceDriver() {
        super(
                "eebus",
                "EEBUS Driver",
                "EEBUS / SHIP energy management stub",
                4712
        );
    }
}
