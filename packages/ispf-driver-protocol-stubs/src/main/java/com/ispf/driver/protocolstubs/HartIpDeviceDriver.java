package com.ispf.driver.protocolstubs;

/**
 * HART-IP protocol stub (hart-ip).
 * <p>
 * HART-IP (UDP/TCP) stub.
 */
public class HartIpDeviceDriver extends ProtocolStubDeviceDriver {

    public HartIpDeviceDriver() {
        super(
                "hart-ip",
                "HART-IP Driver",
                "HART-IP (UDP/TCP) stub",
                5094
        );
    }
}
