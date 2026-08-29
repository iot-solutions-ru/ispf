package com.ispf.driver.protocolstubs;

/**
 * Wireless M-Bus protocol stub (wmbus).
 * <p>
 * Wireless M-Bus (OMS) stub.
 */
public class WmbusDeviceDriver extends ProtocolStubDeviceDriver {

    public WmbusDeviceDriver() {
        super(
                "wmbus",
                "Wireless M-Bus Driver",
                "Wireless M-Bus (OMS) stub",
                10000
        );
    }
}
