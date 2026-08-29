package com.ispf.driver.protocolstubs;

/**
 * CC-Link protocol stub (cc-link).
 * <p>
 * Mitsubishi CC-Link field network stub.
 */
public class CcLinkDeviceDriver extends ProtocolStubDeviceDriver {

    public CcLinkDeviceDriver() {
        super(
                "cc-link",
                "CC-Link Driver",
                "Mitsubishi CC-Link field network stub",
                5001
        );
    }
}
